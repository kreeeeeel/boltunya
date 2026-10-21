import {act, renderHook, waitFor} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
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
    act(() => {
        pending = view.result.current.loadMore();
    });
    await act(() => view.result.current.refresh(true));
    await act(async () => {
        old.resolve(page('old', null));
        await pending;
    });
    expect(view.result.current.entries).toEqual([]);
    expect(view.result.current.loadingMore).toBe(false);
    view.unmount();
});
