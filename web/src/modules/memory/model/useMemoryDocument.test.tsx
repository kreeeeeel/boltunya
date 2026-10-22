import {act, renderHook} from '@testing-library/react';
import {StrictMode, useEffect} from 'react';
import {afterEach, beforeEach, expect, it, vi} from 'vitest';
import type {MemoryCollectionProgress, MemoryDocument} from '@aigram/api-client';
import {ApiRequestError} from '@/shared/api/ApiRequestError';
import {useMemoryDocument} from '@/modules/memory/model/useMemoryDocument';

const mocks = vi.hoisted(() => ({
    getChatMemory: vi.fn(),
    getGlobalMemory: vi.fn(),
    updateChatMemory: vi.fn(),
    updateGlobalMemory: vi.fn(),
    refreshChatMemory: vi.fn(),
    refreshChangedMemory: vi.fn(),
    cancelChatMemoryCollection: vi.fn(),
    cancelGlobalMemoryCollection: vi.fn(),
}));

vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: {memory: mocks},
}));

const progress: MemoryCollectionProgress = {
    active: false,
    status: 'ready',
    queuedChats: 0,
    queuePosition: 0,
    processedMessages: 100,
};
const documentValue: MemoryDocument = {
    chatId: '',
    notes: 'Owner notes',
    summary: 'Existing facts',
    revision: 'notes-before',
    updatedAt: 1000,
    processedMessages: 100,
    backfillComplete: true,
    status: 'ready',
    error: '',
    trackedChats: 2,
    relativePath: 'synthetic/MEMORY.md',
    collection: progress,
};

const collecting: MemoryDocument = {
    ...documentValue,
    status: 'processing',
    collection: {...progress, active: true, status: 'processing'},
};

beforeEach(() => {
    vi.useFakeTimers();
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
    Object.values(mocks).forEach((mock) => mock.mockReset());
    mocks.getGlobalMemory.mockImplementation(async () => structuredClone(documentValue));
    mocks.getChatMemory.mockImplementation(async ({chatId}: {chatId: string}) => ({
        ...structuredClone(documentValue),
        chatId,
    }));
    mocks.updateGlobalMemory.mockImplementation(
        async ({memoryNotesUpdate}: {memoryNotesUpdate: {notes: string}}) => ({
            ...documentValue,
            notes: memoryNotesUpdate.notes,
            revision: 'notes-saved',
        }),
    );
    mocks.updateChatMemory.mockImplementation(
        async ({chatId, memoryNotesUpdate}: {chatId: string; memoryNotesUpdate: {notes: string}}) => ({
            ...documentValue,
            chatId,
            notes: memoryNotesUpdate.notes,
            revision: 'notes-saved',
        }),
    );
    mocks.refreshChangedMemory.mockResolvedValue({ok: true});
    mocks.refreshChatMemory.mockResolvedValue({...collecting, chatId: '42'});
    mocks.cancelGlobalMemoryCollection.mockResolvedValue(documentValue);
    mocks.cancelChatMemoryCollection.mockResolvedValue({...documentValue, chatId: '42'});
});

afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
});

async function tick(milliseconds = 0) {
    await act(async () => {
        await vi.advanceTimersByTimeAsync(milliseconds);
    });
}

function visibility(value: 'visible' | 'hidden') {
    act(() => {
        vi.spyOn(document, 'visibilityState', 'get').mockReturnValue(value);
        document.dispatchEvent(new Event('visibilitychange'));
    });
}

function updated() {
    act(() => {
        window.dispatchEvent(new Event('memory-updated'));
    });
}

it('waits for initial visibility, stops hidden timers and resumes once without losing a local draft', async () => {
    visibility('hidden');
    const view = renderHook(() => useMemoryDocument(''));
    await tick(10000);
    expect(mocks.getGlobalMemory).not.toHaveBeenCalled();
    expect(vi.getTimerCount()).toBe(0);
    visibility('visible');
    await tick();
    expect(mocks.getGlobalMemory).toHaveBeenCalledTimes(1);
    expect(view.result.current.notes).toBe(documentValue.notes);
    act(() => view.result.current.setNotes('Unsaved draft'));
    visibility('hidden');
    updated();
    await tick(10000);
    expect(mocks.getGlobalMemory).toHaveBeenCalledTimes(1);
    expect(vi.getTimerCount()).toBe(0);
    visibility('visible');
    await tick();
    expect(mocks.getGlobalMemory).toHaveBeenCalledTimes(2);
    expect(view.result.current.notes).toBe('Unsaved draft');
    view.unmount();
    await tick(10000);
    expect(mocks.getGlobalMemory).toHaveBeenCalledTimes(2);
    expect(vi.getTimerCount()).toBe(0);
});

it('retains unchanged document identity and commits changed facts even when notes revision and updatedAt are equal', async () => {
    const commits = vi.fn();
    const view = renderHook(() => {
        const result = useMemoryDocument('');
        useEffect(() => {
            commits();
        });
        return result;
    });
    await tick();
    const retained = view.result.current.document;
    const initialCommits = commits.mock.calls.length;
    await tick(5000);
    expect(view.result.current.document).toBe(retained);
    expect(commits).toHaveBeenCalledTimes(initialCommits);
    act(() => view.result.current.setNotes('Keep my draft'));
    mocks.getGlobalMemory.mockResolvedValue({
        ...documentValue,
        summary: 'New facts with identical notes hash',
    });
    await tick(5000);
    expect(view.result.current.document?.summary).toBe('New facts with identical notes hash');
    expect(view.result.current.document?.revision).toBe(documentValue.revision);
    expect(view.result.current.notes).toBe('Keep my draft');
    view.unmount();
});

it.each(['resolve', 'reject'] as const)(
    'ignores an aborted hidden GET that later %s after a fresh visible result',
    async (outcome) => {
        const old = Promise.withResolvers<MemoryDocument>();
        mocks.getGlobalMemory.mockReturnValueOnce(old.promise);
        const view = renderHook(() => useMemoryDocument(''));
        await tick();
        const signal = mocks.getGlobalMemory.mock.calls[0][0].signal as AbortSignal;
        visibility('hidden');
        expect(signal.aborted).toBe(true);
        visibility('visible');
        await tick();
        const confirmed = view.result.current.document;
        if (outcome === 'resolve') {
            old.resolve(collecting);
        } else {
            old.reject(new Error('Obsolete read failure'));
        }
        await tick();
        expect(view.result.current.document).toBe(confirmed);
        expect(view.result.current.error).toBe('');
        expect(mocks.getGlobalMemory).toHaveBeenCalledTimes(2);
        view.unmount();
    },
);

it('starts only the surviving StrictMode GET and aborts it on unmount without scheduling another poll', async () => {
    const pending = Promise.withResolvers<MemoryDocument>();
    mocks.getChatMemory.mockReturnValueOnce(pending.promise);
    const view = renderHook(() => useMemoryDocument('42'), {wrapper: StrictMode});
    await tick();
    expect(mocks.getChatMemory).toHaveBeenCalledTimes(1);
    expect(mocks.getChatMemory.mock.calls[0][0]).toEqual({chatId: '42'});
    const signal = mocks.getChatMemory.mock.calls[0][1].signal as AbortSignal;
    view.unmount();
    expect(signal.aborted).toBe(true);
    pending.resolve({...documentValue, chatId: '42'});
    await tick(10000);
    expect(mocks.getChatMemory).toHaveBeenCalledTimes(1);
    expect(vi.getTimerCount()).toBe(0);
});

it('makes Reload await a fresh follow-up to an in-flight poll and explicitly replaces the notes base', async () => {
    const old = Promise.withResolvers<MemoryDocument>();
    const fresh = Promise.withResolvers<MemoryDocument>();
    mocks.getGlobalMemory
        .mockResolvedValueOnce(documentValue)
        .mockReturnValueOnce(old.promise)
        .mockReturnValueOnce(fresh.promise);
    const view = renderHook(() => useMemoryDocument(''));
    await tick();
    act(() => view.result.current.setNotes('Discard this unsaved draft'));
    updated();
    await tick();
    let reload = Promise.resolve();
    let settled = false;
    act(() => {
        reload = view.result.current.reload().then(() => {
            settled = true;
        });
    });
    expect(mocks.getGlobalMemory).toHaveBeenCalledTimes(2);
    old.resolve({...collecting, notes: 'Old file notes'});
    await tick();
    expect(mocks.getGlobalMemory).toHaveBeenCalledTimes(3);
    expect(settled).toBe(false);
    expect(view.result.current.busy).toBe(true);
    expect(view.result.current.notes).toBe('Discard this unsaved draft');
    await act(async () => {
        fresh.resolve({...documentValue, notes: 'Fresh file notes', revision: 'file-revision'});
        await reload;
    });
    expect(settled).toBe(true);
    expect(view.result.current.notes).toBe('Fresh file notes');
    act(() => view.result.current.setNotes('New owner edit'));
    await act(async () => {
        await view.result.current.save();
    });
    expect(mocks.updateGlobalMemory).toHaveBeenCalledExactlyOnceWith({
        memoryNotesUpdate: {notes: 'New owner edit', revision: 'file-revision'},
    });
    view.unmount();
});

it('invalidates pre-save GETs, pauses reads during a write and preserves text typed after submission', async () => {
    const old = Promise.withResolvers<MemoryDocument>();
    const saved = Promise.withResolvers<MemoryDocument>();
    mocks.getGlobalMemory.mockResolvedValueOnce(documentValue).mockReturnValueOnce(old.promise);
    mocks.updateGlobalMemory.mockReturnValueOnce(saved.promise);
    const view = renderHook(() => useMemoryDocument(''));
    await tick();
    updated();
    await tick();
    const oldSignal = mocks.getGlobalMemory.mock.calls[1][0].signal as AbortSignal;
    act(() => view.result.current.setNotes('Submitted notes'));
    let save = Promise.resolve(false);
    act(() => {
        save = view.result.current.save();
    });
    expect(oldSignal.aborted).toBe(true);
    await tick(10000);
    expect(mocks.getGlobalMemory).toHaveBeenCalledTimes(2);
    expect(view.result.current.busy).toBe(true);
    act(() => view.result.current.setNotes('Typed while saving'));
    await act(async () => {
        saved.resolve({...documentValue, notes: 'Submitted notes', revision: 'confirmed-save'});
        expect(await save).toBe(true);
    });
    old.resolve(collecting);
    await tick();
    expect(view.result.current.document?.notes).toBe('Submitted notes');
    expect(view.result.current.document?.revision).toBe('confirmed-save');
    expect(view.result.current.notes).toBe('Typed while saving');
    expect(view.result.current.busy).toBe(false);
    await act(async () => {
        await view.result.current.save();
    });
    expect(mocks.updateGlobalMemory.mock.calls[1][0]).toEqual({
        memoryNotesUpdate: {notes: 'Typed while saving', revision: 'confirmed-save'},
    });
    view.unmount();
});

it.each(['cancel', 'refresh'] as const)(
    'keeps confirmed chat collection state after %s despite an old GET',
    async (action) => {
        const old = Promise.withResolvers<MemoryDocument>();
        const initial = {...collecting, chatId: '42'};
        const confirmed: MemoryDocument = {
            ...documentValue,
            chatId: '42',
            status: 'queued',
            collection: {...progress, active: true, status: 'queued'},
        };
        mocks.getChatMemory.mockResolvedValueOnce(initial).mockReturnValueOnce(old.promise);
        mocks.refreshChatMemory.mockResolvedValueOnce(confirmed);
        const view = renderHook(() => useMemoryDocument('42'));
        await tick();
        act(() => view.result.current.setNotes('Unsaved owner notes'));
        updated();
        await tick();
        const signal = mocks.getChatMemory.mock.calls[1][1].signal as AbortSignal;
        await act(async () => {
            if (action === 'cancel') {
                await view.result.current.cancel();
            } else {
                expect(await view.result.current.refresh(true, true)).toBe(true);
            }
        });
        expect(signal.aborted).toBe(true);
        old.resolve(initial);
        await tick();
        expect(view.result.current.notes).toBe('Unsaved owner notes');
        expect(view.result.current.document?.collection?.active).toBe(action === 'refresh');
        if (action === 'cancel') {
            expect(mocks.cancelChatMemoryCollection).toHaveBeenCalledExactlyOnceWith({chatId: '42'});
        } else {
            expect(mocks.refreshChatMemory).toHaveBeenCalledExactlyOnceWith({
                chatId: '42',
                memoryRefreshRequest: {rebuild: true, immediate: true},
            });
            expect(view.result.current.document?.status).toBe('queued');
        }
        view.unmount();
    },
);

it.each(['ready', 'failed'] as const)(
    'awaits a fresh global read after confirmed collection and handles a %s read without repeating the POST',
    async (outcome) => {
        const fresh = Promise.withResolvers<MemoryDocument>();
        mocks.getGlobalMemory.mockResolvedValueOnce(documentValue).mockReturnValueOnce(fresh.promise);
        const view = renderHook(() => useMemoryDocument(''));
        await tick();
        let refresh = Promise.resolve(false);
        let settled = false;
        act(() => {
            refresh = view.result.current.refresh(true, true).then((result) => {
                settled = true;
                return result;
            });
        });
        await tick();
        expect(mocks.refreshChangedMemory).toHaveBeenCalledExactlyOnceWith({
            memoryRefreshRequest: {rebuild: true, immediate: true},
        });
        expect(mocks.getGlobalMemory).toHaveBeenCalledTimes(2);
        expect(settled).toBe(false);
        expect(view.result.current.busy).toBe(true);
        await act(async () => {
            if (outcome === 'ready') {
                fresh.resolve(collecting);
            } else {
                fresh.reject(new Error('Read failed after confirmed collection'));
            }
            expect(await refresh).toBe(true);
        });
        expect(view.result.current.busy).toBe(false);
        expect(view.result.current.error).toBe(
            outcome === 'ready' ? '' : 'Read failed after confirmed collection',
        );
        await tick(4999);
        expect(mocks.getGlobalMemory).toHaveBeenCalledTimes(2);
        expect(mocks.refreshChangedMemory).toHaveBeenCalledTimes(1);
        view.unmount();
    },
);

it('preserves a 409 conflict and its local notes base until explicit Reload without retrying the write', async () => {
    const external = {...documentValue, notes: 'Changed in another editor', revision: 'external-revision'};
    const conflict = new ApiRequestError('Memory notes changed', 409, 'MEMORY_NOTES_CONFLICT');
    mocks.updateGlobalMemory.mockRejectedValueOnce(conflict);
    const view = renderHook(() => useMemoryDocument(''));
    await tick();
    act(() => view.result.current.setNotes('My unsaved notes'));
    mocks.getGlobalMemory.mockResolvedValue(external);
    await tick(5000);
    await act(async () => {
        expect(await view.result.current.save()).toBe(false);
    });
    expect(mocks.updateGlobalMemory).toHaveBeenCalledExactlyOnceWith({
        memoryNotesUpdate: {notes: 'My unsaved notes', revision: documentValue.revision},
    });
    expect(view.result.current.error).toBe(conflict.message);
    await tick(5000);
    expect(view.result.current.notes).toBe('My unsaved notes');
    expect(view.result.current.error).toBe(conflict.message);
    expect(mocks.updateGlobalMemory).toHaveBeenCalledTimes(1);
    await act(async () => {
        await view.result.current.reload();
    });
    expect(view.result.current.notes).toBe(external.notes);
    expect(view.result.current.error).toBe('');
    view.unmount();
});

it('guards simultaneous public actions before busy rerenders without sending a second write or reopening polling', async () => {
    const saved = Promise.withResolvers<MemoryDocument>();
    mocks.updateGlobalMemory.mockReturnValueOnce(saved.promise);
    const view = renderHook(() => useMemoryDocument(''));
    await tick();
    let first = Promise.resolve(false);
    let duplicate = Promise.resolve(false);
    act(() => {
        first = view.result.current.save();
        duplicate = view.result.current.refresh(true, true);
        void view.result.current.cancel();
    });
    expect(await duplicate).toBe(false);
    await tick(10000);
    expect(mocks.refreshChangedMemory).not.toHaveBeenCalled();
    expect(mocks.cancelGlobalMemoryCollection).not.toHaveBeenCalled();
    expect(mocks.updateGlobalMemory).toHaveBeenCalledTimes(1);
    expect(mocks.getGlobalMemory).toHaveBeenCalledTimes(1);
    expect(view.result.current.busy).toBe(true);
    await act(async () => {
        saved.resolve(documentValue);
        expect(await first).toBe(true);
    });
    expect(view.result.current.busy).toBe(false);
    view.unmount();
});

it('rejects an old-chat mutation completion after switching away and back to the same id', async () => {
    const saved = Promise.withResolvers<MemoryDocument>();
    mocks.updateChatMemory.mockReturnValueOnce(saved.promise);
    const view = renderHook(({id}) => useMemoryDocument(id), {initialProps: {id: '42'}});
    await tick();
    act(() => view.result.current.setNotes('Old session draft'));
    let pending = Promise.resolve(false);
    act(() => {
        pending = view.result.current.save();
    });
    view.rerender({id: '43'});
    expect(view.result.current.document).toBeUndefined();
    expect(view.result.current.notes).toBe('');
    await tick();
    expect(view.result.current.document?.chatId).toBe('43');
    view.rerender({id: '42'});
    await tick();
    act(() => view.result.current.setNotes('New session draft'));
    await act(async () => {
        saved.resolve({
            ...documentValue,
            chatId: '42',
            notes: 'Old confirmed response',
            revision: 'old-response',
        });
        expect(await pending).toBe(false);
    });
    expect(view.result.current.notes).toBe('New session draft');
    expect(view.result.current.document?.revision).toBe(documentValue.revision);
    expect(view.result.current.error).toBe('');
    expect(view.result.current.busy).toBe(false);
    view.unmount();
});

it('does not publish or re-read after a pending mutation finishes following unmount', async () => {
    const saved = Promise.withResolvers<MemoryDocument>();
    mocks.updateGlobalMemory.mockReturnValueOnce(saved.promise);
    const event = vi.fn();
    window.addEventListener('memory-updated', event);
    const view = renderHook(() => useMemoryDocument(''));
    await tick();
    let pending = Promise.resolve(false);
    act(() => {
        pending = view.result.current.save();
    });
    view.unmount();
    await act(async () => {
        saved.resolve(documentValue);
        expect(await pending).toBe(false);
    });
    await tick(10000);
    expect(mocks.updateGlobalMemory).toHaveBeenCalledTimes(1);
    expect(mocks.getGlobalMemory).toHaveBeenCalledTimes(1);
    expect(event).not.toHaveBeenCalled();
    expect(vi.getTimerCount()).toBe(0);
    window.removeEventListener('memory-updated', event);
});
