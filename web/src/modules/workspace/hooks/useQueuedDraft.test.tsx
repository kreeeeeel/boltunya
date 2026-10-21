import {act, renderHook} from '@testing-library/react';
import {NextIntlClientProvider} from 'next-intl';
import type {ReactNode} from 'react';
import {expect, it, vi} from 'vitest';
import {useQueuedDraft} from '@/modules/workspace/hooks/useQueuedDraft';
import {defaultRule} from '@/shared/api/client';
import ru from '@/shared/i18n/ru.json';
const mocks = vi.hoisted(() => ({POST: vi.fn(), GET: vi.fn(), DELETE: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: mocks,
}));
function wrapper({children}: {children: ReactNode}) {
    return (
        <NextIntlClientProvider locale="ru" messages={ru}>
            {children}
        </NextIntlClientProvider>
    );
}
function reply(status: string, text = '') {
    return {
        data: {
            id: 'draft-1',
            status,
            result: text ? {text, durationMs: 100, tokensPerSecond: 10} : null,
            error: '',
        },
        response: new Response(),
    };
}
it('uses the shared queue, polls progress and returns only a ready draft', async () => {
    vi.useFakeTimers();
    const refresh = vi.fn(async () => {});
    mocks.POST.mockResolvedValue(reply('queued'));
    mocks.GET.mockResolvedValueOnce(reply('generating')).mockResolvedValueOnce(
        reply('ready', 'Короткий ответ'),
    );
    try {
        const view = renderHook(() => useQueuedDraft('42', refresh), {wrapper});
        let output: Promise<string>;
        await act(async () => {
            output = view.result.current(defaultRule, '99');
            await vi.advanceTimersByTimeAsync(1500);
            expect(await output).toBe('Короткий ответ');
        });
        expect(mocks.POST).toHaveBeenCalledWith(
            '/api/preview/jobs',
            expect.objectContaining({body: {chatId: '42', rule: defaultRule, messageId: '99'}}),
        );
        expect(mocks.GET).toHaveBeenCalledTimes(2);
        expect(refresh).toHaveBeenCalledTimes(2);
        view.unmount();
    } finally {
        vi.useRealTimers();
    }
});
it('stops polling on navigation without cancelling the queued job', async () => {
    vi.useFakeTimers();
    mocks.POST.mockClear().mockResolvedValue(reply('queued'));
    mocks.GET.mockClear();
    mocks.DELETE.mockClear();
    try {
        const view = renderHook(({id}) => useQueuedDraft(id, async () => {}), {
            initialProps: {id: '42'},
            wrapper,
        });
        let output: Promise<unknown>;
        await act(async () => {
            output = view.result.current(defaultRule).catch((cause) => cause);
            await vi.advanceTimersByTimeAsync(0);
        });
        view.rerender({id: '43'});
        await act(async () => {
            await vi.advanceTimersByTimeAsync(1500);
        });
        expect(await output!).toMatchObject({name: 'AbortError'});
        expect(mocks.GET).not.toHaveBeenCalled();
        expect(mocks.DELETE).not.toHaveBeenCalled();
        view.unmount();
    } finally {
        vi.useRealTimers();
    }
});
it('reports a cancelled job without returning an empty reply', async () => {
    mocks.POST.mockResolvedValue(reply('cancelled'));
    const view = renderHook(() => useQueuedDraft('42', async () => {}), {wrapper});
    await expect(view.result.current(defaultRule)).rejects.toThrow(ru.draftQueue.cancelled);
    view.unmount();
});
