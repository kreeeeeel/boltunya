import {act, renderHook} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {useWorkspace} from '@/modules/workspace/hooks/useWorkspace';
import type {Message} from '@/shared/api/client';
import {workspaceState} from '@tests/fixtures/workspace';

const mocks = vi.hoisted(() => ({state: vi.fn(), history: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: {system: {state: mocks.state}, chats: {history: mocks.history}},
}));
vi.mock('@/shared/desktop/bridge', () => ({desktopBridge: () => undefined}));

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
