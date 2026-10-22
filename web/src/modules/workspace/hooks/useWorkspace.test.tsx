import {act, renderHook} from '@testing-library/react';
import {afterEach, beforeEach, expect, it, vi} from 'vitest';
import {useEffect} from 'react';
import {useWorkspace} from '@/modules/workspace/hooks/useWorkspace';
import type {Message} from '@/shared/api/client';
import {workspaceState} from '@tests/fixtures/workspace';

const mocks = vi.hoisted(() => ({state: vi.fn(), history: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: {system: {state: mocks.state}, chats: {history: mocks.history}},
}));
vi.mock('@/shared/desktop/bridge', () => ({desktopBridge: () => undefined}));

beforeEach(() => {
    vi.useFakeTimers();
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
    mocks.state.mockReset().mockResolvedValue(workspaceState);
    mocks.history.mockReset().mockResolvedValue([]);
});

afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
});

it('ignores a pre-edit history request even when Telegram edits share the same second', async () => {
    vi.useFakeTimers();
    const original: Message = {
        id: '123',
        chatId: '42',
        text: 'Original',
        outgoing: true,
        kind: 'text',
        date: 1,
        editedAt: 100,
    };
    const updated = {...original, text: 'Saved edit'};
    let resolveHistory: (messages: Message[]) => void = () => {};
    const stale = new Promise<Message[]>((resolve) => {
        resolveHistory = resolve;
    });
    mocks.state.mockResolvedValue(workspaceState);
    mocks.history
        .mockResolvedValueOnce([original])
        .mockReturnValueOnce(stale)
        .mockResolvedValue([{...updated, read: true}]);
    const view = renderHook(() => useWorkspace('42'));
    try {
        await act(async () => {
            await vi.advanceTimersByTimeAsync(0);
        });
        expect(view.result.current.messages[0].text).toBe('Original');
        await act(async () => {
            await vi.advanceTimersByTimeAsync(2500);
        });
        expect(mocks.history).toHaveBeenCalledTimes(2);
        act(() => view.result.current.updateMessage(updated));
        await act(async () => {
            resolveHistory([original]);
        });
        expect(view.result.current.messages[0].text).toBe('Saved edit');
        await act(async () => {
            await vi.advanceTimersByTimeAsync(2500);
        });
        expect(view.result.current.messages[0]).toMatchObject({text: 'Saved edit', read: true});
    } finally {
        view.unmount();
        vi.useRealTimers();
    }
});

it('stops only chat history on another tab and resumes immediately without clearing retained messages', async () => {
    const message: Message = {
        id: '1',
        chatId: '42',
        text: 'Retained',
        outgoing: false,
        date: 1,
        kind: 'text',
    };
    mocks.history.mockResolvedValue([message]);
    const view = renderHook(({active}) => useWorkspace('42', active), {initialProps: {active: true}});
    await act(() => vi.advanceTimersByTimeAsync(0));
    expect(mocks.history).toHaveBeenCalledTimes(1);
    const retained = view.result.current.messages;
    view.rerender({active: false});
    await act(() => vi.advanceTimersByTimeAsync(7500));
    expect(mocks.history).toHaveBeenCalledTimes(1);
    expect(mocks.state).toHaveBeenCalledTimes(4);
    expect(view.result.current.messages).toBe(retained);
    view.rerender({active: true});
    await act(() => vi.advanceTimersByTimeAsync(0));
    expect(mocks.history).toHaveBeenCalledTimes(2);
    expect(view.result.current.messages).toBe(retained);
    view.unmount();
});

it('pauses both automatic polls while hidden and resumes them immediately on visibility change', async () => {
    const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');
    const view = renderHook(() => useWorkspace('42'));
    await act(() => vi.advanceTimersByTimeAsync(10000));
    expect(mocks.state).not.toHaveBeenCalled();
    expect(mocks.history).not.toHaveBeenCalled();
    visibility.mockReturnValue('visible');
    await act(async () => {
        document.dispatchEvent(new Event('visibilitychange'));
    });
    expect(mocks.state).toHaveBeenCalledTimes(1);
    expect(mocks.history).toHaveBeenCalledTimes(1);
    visibility.mockReturnValue('hidden');
    await act(async () => {
        document.dispatchEvent(new Event('visibilitychange'));
    });
    await act(() => vi.advanceTimersByTimeAsync(10000));
    expect(mocks.state).toHaveBeenCalledTimes(1);
    expect(mocks.history).toHaveBeenCalledTimes(1);
    await act(async () => view.result.current.refresh());
    expect(mocks.state).toHaveBeenCalledTimes(2);
    visibility.mockReturnValue('visible');
    await act(async () => {
        document.dispatchEvent(new Event('visibilitychange'));
    });
    expect(mocks.state).toHaveBeenCalledTimes(3);
    expect(mocks.history).toHaveBeenCalledTimes(2);
    view.unmount();
    await act(() => vi.advanceTimersByTimeAsync(10000));
    expect(mocks.state).toHaveBeenCalledTimes(3);
    expect(mocks.history).toHaveBeenCalledTimes(2);
});

it('does not commit unchanged history status or structurally identical snapshots on every poll', async () => {
    const message: Message = {
        id: '1',
        chatId: '42',
        text: 'Unchanged',
        outgoing: false,
        date: 1,
        kind: 'text',
    };
    mocks.state.mockImplementation(async () => structuredClone(workspaceState));
    mocks.history.mockImplementation(async () => [structuredClone(message)]);
    const commits = vi.fn();
    const view = renderHook(() => {
        const workspace = useWorkspace('42');
        useEffect(() => {
            commits();
        });
        return workspace;
    });
    await act(() => vi.advanceTimersByTimeAsync(0));
    const initial = commits.mock.calls.length;
    await act(() => vi.advanceTimersByTimeAsync(7500));
    expect(mocks.state).toHaveBeenCalledTimes(4);
    expect(mocks.history).toHaveBeenCalledTimes(4);
    expect(commits).toHaveBeenCalledTimes(initial);
    view.unmount();
});

it('does not overlap scheduled state requests and an explicit refresh supersedes an older response', async () => {
    let finish: (value: typeof workspaceState) => void = () => {};
    mocks.state.mockReturnValueOnce(
        new Promise((resolve) => {
            finish = resolve;
        }),
    );
    const view = renderHook(() => useWorkspace(null));
    await act(() => vi.advanceTimersByTimeAsync(10000));
    expect(mocks.state).toHaveBeenCalledTimes(1);
    const signal = mocks.state.mock.calls[0][0].signal as AbortSignal;
    const current = {...workspaceState, settings: {...workspaceState.settings, paused: false}};
    mocks.state.mockResolvedValue(current);
    await act(async () => view.result.current.refresh());
    expect(signal.aborted).toBe(true);
    expect(mocks.state).toHaveBeenCalledTimes(2);
    expect(view.result.current.state?.settings.paused).toBe(false);
    await act(async () => finish(workspaceState));
    expect(view.result.current.state?.settings.paused).toBe(false);
    view.unmount();
});

it('aborts pending state and history requests when the workspace unmounts', () => {
    mocks.state.mockReturnValue(new Promise(() => {}));
    mocks.history.mockReturnValue(new Promise(() => {}));
    const view = renderHook(() => useWorkspace('42'));
    const stateSignal = mocks.state.mock.calls[0][0].signal as AbortSignal;
    const historySignal = mocks.history.mock.calls[0][1].signal as AbortSignal;
    expect(stateSignal.aborted).toBe(false);
    expect(historySignal.aborted).toBe(false);
    view.unmount();
    expect(stateSignal.aborted).toBe(true);
    expect(historySignal.aborted).toBe(true);
});
