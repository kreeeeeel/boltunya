import {act, renderHook, waitFor} from '@testing-library/react';
import {StrictMode} from 'react';
import {afterEach, beforeEach, expect, it, vi} from 'vitest';
import type {ActivityHistoryPage} from '@aigram/api-client';
import {useActivityHistory} from '@/modules/activity/model/useActivityHistory';
import {historyPage} from '@tests/fixtures/activityHistory';
import {testActivity} from '@tests/fixtures/activity';
const mocks = vi.hoisted(() => ({activityHistory: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: {
        activity: {
            activityHistory: mocks.activityHistory,
        },
    },
}));
const page = (id: string, nextCursor: string | null, date = 1000) => ({
    ...historyPage([{...testActivity[2], id, date}]),
    nextCursor,
    hasMore: !!nextCursor,
});

beforeEach(() => {
    mocks.activityHistory.mockReset();
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
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
it('retains older pages across polling and continues from the last loaded cursor', async () => {
    mocks.activityHistory
        .mockReset()
        .mockResolvedValueOnce(page('new', 'cursor-1', 3))
        .mockResolvedValueOnce(page('old', 'cursor-2', 2))
        .mockResolvedValueOnce(page('latest', 'new-first-page', 4))
        .mockResolvedValueOnce(page('oldest', null, 1));
    const view = renderHook(useActivityHistory);
    await waitFor(() => expect(view.result.current.loading).toBe(false));
    await act(() => view.result.current.loadMore());
    await act(() => view.result.current.refresh());
    expect(view.result.current.entries.map((entry) => entry.id)).toEqual(['latest', 'new', 'old']);
    await act(() => view.result.current.loadMore());
    expect(mocks.activityHistory.mock.calls[3][0].cursor).toBe('cursor-2');
    expect(view.result.current.entries).toHaveLength(4);
    view.unmount();
});
it('does not restore cleared events when an older pagination request finishes late', async () => {
    const old = Promise.withResolvers<unknown>();
    mocks.activityHistory
        .mockReset()
        .mockResolvedValueOnce(page('new', 'cursor-1'))
        .mockReturnValueOnce(old.promise)
        .mockResolvedValueOnce(historyPage());
    const view = renderHook(useActivityHistory);
    await waitFor(() => expect(view.result.current.loading).toBe(false));
    let pending: Promise<void>;
    await act(async () => {
        pending = view.result.current.loadMore();
        await Promise.resolve();
    });
    expect(mocks.activityHistory).toHaveBeenCalledTimes(2);
    await act(() => view.result.current.refresh(true));
    await act(async () => {
        old.resolve(page('old', null));
        await pending;
    });
    expect(view.result.current.entries).toEqual([]);
    expect(view.result.current.loadingMore).toBe(false);
    view.unmount();
});

it('waits for initial visibility and leaves no timer after unmount', async () => {
    vi.useFakeTimers();
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');
    mocks.activityHistory.mockResolvedValue(page('current', null));
    const view = renderHook(useActivityHistory);
    await tick(10000);
    expect(mocks.activityHistory).not.toHaveBeenCalled();
    expect(vi.getTimerCount()).toBe(0);
    expect(view.result.current.loading).toBe(true);

    visibility('visible');
    await tick();
    expect(mocks.activityHistory).toHaveBeenCalledTimes(1);
    expect(view.result.current.entries[0].id).toBe('current');
    expect(view.result.current.loading).toBe(false);
    expect(vi.getTimerCount()).toBe(1);
    view.unmount();
    await tick(10000);
    expect(mocks.activityHistory).toHaveBeenCalledTimes(1);
    expect(vi.getTimerCount()).toBe(0);
});

it('aborts hidden pagination, clears its busy flag and ignores a response that disregards abort', async () => {
    vi.useFakeTimers();
    const stale = Promise.withResolvers<ActivityHistoryPage>();
    mocks.activityHistory
        .mockResolvedValueOnce(page('current', 'first-cursor'))
        .mockReturnValueOnce(stale.promise)
        .mockResolvedValue(page('latest', 'fresh-cursor'));
    const view = renderHook(useActivityHistory);
    await tick();
    act(() => {
        void view.result.current.loadMore();
    });
    await tick();
    const signal = mocks.activityHistory.mock.calls[1][1].signal as AbortSignal;
    expect(view.result.current.loadingMore).toBe(true);
    visibility('hidden');
    expect(signal.aborted).toBe(true);
    expect(view.result.current.loadingMore).toBe(false);
    stale.resolve(page('stale', null));
    await tick(10000);
    expect(mocks.activityHistory).toHaveBeenCalledTimes(2);
    expect(view.result.current.entries.map((entry) => entry.id)).toEqual(['current']);
    expect(vi.getTimerCount()).toBe(0);

    visibility('visible');
    await tick();
    expect(mocks.activityHistory).toHaveBeenCalledTimes(3);
    expect(view.result.current.entries.map((entry) => entry.id)).toContain('latest');
    await act(() => view.result.current.loadMore());
    expect(mocks.activityHistory.mock.calls[3][0].cursor).toBe('fresh-cursor');
    view.unmount();
});

it('coalesces refreshes during a head read and awaits a mandatory post-mutation response', async () => {
    vi.useFakeTimers();
    const stale = Promise.withResolvers<ActivityHistoryPage>();
    const fresh = Promise.withResolvers<ActivityHistoryPage>();
    mocks.activityHistory.mockReturnValueOnce(stale.promise).mockReturnValueOnce(fresh.promise);
    const view = renderHook(useActivityHistory);
    await tick();
    let first = Promise.resolve();
    let second = Promise.resolve();
    let settled = 0;
    act(() => {
        first = view.result.current.refresh().then(() => {
            settled += 1;
        });
        second = view.result.current.refresh().then(() => {
            settled += 1;
        });
    });
    expect(mocks.activityHistory).toHaveBeenCalledTimes(1);
    stale.resolve(page('obsolete', 'stale-cursor'));
    await tick();
    expect(mocks.activityHistory).toHaveBeenCalledTimes(2);
    expect(view.result.current.entries).toEqual([]);
    expect(view.result.current.loading).toBe(true);
    expect(settled).toBe(0);

    await act(async () => {
        fresh.resolve(page('confirmed', 'fresh-cursor'));
        await Promise.all([first, second]);
    });
    expect(settled).toBe(2);
    expect(view.result.current.entries.map((entry) => entry.id)).toEqual(['confirmed']);
    expect(view.result.current.loading).toBe(false);
    expect(mocks.activityHistory).toHaveBeenCalledTimes(2);
    expect(vi.getTimerCount()).toBe(1);
    view.unmount();
});

it('settles a failed mandatory refresh without an immediate retry loop', async () => {
    vi.useFakeTimers();
    const stale = Promise.withResolvers<ActivityHistoryPage>();
    mocks.activityHistory
        .mockReturnValueOnce(stale.promise)
        .mockRejectedValueOnce(new Error('Synthetic fresh read failed'))
        .mockResolvedValue(page('recovered', null));
    const view = renderHook(useActivityHistory);
    await tick();
    let pending = Promise.resolve();
    act(() => {
        pending = view.result.current.refresh();
    });
    await act(async () => {
        stale.resolve(page('obsolete', null));
        await pending;
    });
    expect(mocks.activityHistory).toHaveBeenCalledTimes(2);
    expect(view.result.current.error).toBe('Synthetic fresh read failed');
    expect(view.result.current.loading).toBe(false);
    await tick(4999);
    expect(mocks.activityHistory).toHaveBeenCalledTimes(2);
    await tick(1);
    expect(mocks.activityHistory).toHaveBeenCalledTimes(3);
    expect(view.result.current.error).toBe('');
    view.unmount();
});

it('retains useful older entries while a late page cannot undo newer head feedback or metadata', async () => {
    vi.useFakeTimers();
    const old = Promise.withResolvers<ActivityHistoryPage>();
    const initial = page('same-id', 'first-cursor', 3);
    const fresh = {
        ...page('same-id', 'new-head-cursor', 3),
        entries: [{...initial.entries[0], feedbackRating: 'good' as const}],
        total: 42,
        storage: {...initial.storage, bytes: 9000},
    };
    mocks.activityHistory
        .mockResolvedValueOnce(initial)
        .mockReturnValueOnce(old.promise)
        .mockResolvedValueOnce(fresh)
        .mockResolvedValue(page('oldest', null, 1));
    const view = renderHook(useActivityHistory);
    await tick();
    let older = Promise.resolve();
    let duplicate = Promise.resolve();
    act(() => {
        older = view.result.current.loadMore();
        duplicate = view.result.current.loadMore();
    });
    await tick();
    expect(duplicate).toBe(older);
    expect(mocks.activityHistory).toHaveBeenCalledTimes(2);
    await act(() => view.result.current.refresh());
    await act(async () => {
        old.resolve({
            ...page('older', 'second-cursor', 2),
            entries: [initial.entries[0], ...page('older', null, 2).entries],
            total: 2,
            storage: {...initial.storage, bytes: 100},
        });
        await older;
    });
    expect(view.result.current.entries.map((entry) => entry.id)).toEqual(['same-id', 'older']);
    expect(view.result.current.entries[0].feedbackRating).toBe('good');
    expect(view.result.current.total).toBe(42);
    expect(view.result.current.storage?.bytes).toBe(9000);
    expect(view.result.current.loadingMore).toBe(false);
    await act(() => view.result.current.loadMore());
    expect(mocks.activityHistory.mock.calls[3][0].cursor).toBe('second-cursor');
    expect(view.result.current.entries).toHaveLength(3);
    view.unmount();
});

it('keeps a failed reset pending until its retry replaces the cleared archive and establishes a new cursor', async () => {
    vi.useFakeTimers();
    mocks.activityHistory
        .mockResolvedValueOnce(page('removed', 'old-cursor'))
        .mockRejectedValueOnce(new Error('Synthetic reset read failed'))
        .mockResolvedValue(historyPage());
    const view = renderHook(useActivityHistory);
    await tick();
    await act(() => view.result.current.refresh(true));
    expect(view.result.current.hasMore).toBe(false);
    await act(() => view.result.current.loadMore());
    expect(mocks.activityHistory).toHaveBeenCalledTimes(2);
    await tick(5000);
    expect(view.result.current.entries).toEqual([]);
    expect(view.result.current.total).toBe(0);
    expect(view.result.current.error).toBe('');
    view.unmount();
});

it('does not start a discarded StrictMode request and aborts its surviving read on unmount', async () => {
    vi.useFakeTimers();
    const pending = Promise.withResolvers<ActivityHistoryPage>();
    mocks.activityHistory.mockReturnValue(pending.promise);
    const view = renderHook(useActivityHistory, {wrapper: StrictMode});
    await tick();
    expect(mocks.activityHistory).toHaveBeenCalledTimes(1);
    const signal = mocks.activityHistory.mock.calls[0][1].signal as AbortSignal;
    view.unmount();
    expect(signal.aborted).toBe(true);
    pending.resolve(page('late', null));
    await tick(10000);
    expect(mocks.activityHistory).toHaveBeenCalledTimes(1);
    expect(vi.getTimerCount()).toBe(0);
});
