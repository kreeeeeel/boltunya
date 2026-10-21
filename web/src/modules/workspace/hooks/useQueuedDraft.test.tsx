import {act, renderHook} from '@testing-library/react';
import {NextIntlClientProvider} from 'next-intl';
import type {ReactNode} from 'react';
import {expect, it, vi} from 'vitest';
import {useQueuedDraft} from '@/modules/workspace/hooks/useQueuedDraft';
import {defaultRule} from '@/shared/api/client';
import ru from '@/shared/i18n/ru.json';
import type {Reply} from '@/shared/api/client';
const mocks = vi.hoisted(() => ({enqueuePreviewJob: vi.fn(), previewJob: vi.fn(), cancelActivity: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: {
        activity: {cancelActivity: mocks.cancelActivity},
        models: {
            enqueuePreviewJob: mocks.enqueuePreviewJob,
            previewJob: mocks.previewJob,
        },
    },
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
        id: 'draft-1',
        status,
        result: text ? {text, durationMs: 100, tokensPerSecond: 10} : null,
        error: '',
    };
}
it('uses the shared queue, polls progress and returns only a ready draft', async () => {
    vi.useFakeTimers();
    const refresh = vi.fn(async () => {});
    mocks.enqueuePreviewJob.mockResolvedValue(reply('queued'));
    mocks.previewJob
        .mockResolvedValueOnce(reply('generating'))
        .mockResolvedValueOnce(reply('ready', 'Короткий ответ'));
    try {
        const view = renderHook(() => useQueuedDraft('42', refresh), {wrapper});
        let output: Promise<Reply>;
        await act(async () => {
            output = view.result.current(defaultRule, '99');
            await vi.advanceTimersByTimeAsync(1500);
            expect(await output).toMatchObject({text: 'Короткий ответ', durationMs: 100});
        });
        expect(mocks.enqueuePreviewJob).toHaveBeenCalledWith(
            expect.objectContaining({
                previewJobRequest: {chatId: '42', rule: defaultRule, messageId: '99'},
            }),
            expect.objectContaining({signal: expect.any(AbortSignal)}),
        );
        expect(mocks.previewJob).toHaveBeenCalledTimes(2);
        expect(refresh).toHaveBeenCalledTimes(2);
        view.unmount();
    } finally {
        vi.useRealTimers();
    }
});

it('preserves the actual Telegram entities in a ready generated draft', async () => {
    const result = {
        text: 'Привет',
        entities: [{offset: 0, length: 6, type: 'bold'}],
        durationMs: 100,
        tokensPerSecond: 10,
    };
    mocks.enqueuePreviewJob.mockResolvedValue({...reply('ready'), result});
    const view = renderHook(() => useQueuedDraft('42', async () => {}), {wrapper});
    expect(await view.result.current(defaultRule)).toEqual(result);
    view.unmount();
});
it('stops polling on navigation without cancelling the queued job', async () => {
    vi.useFakeTimers();
    mocks.enqueuePreviewJob.mockClear().mockResolvedValue(reply('queued'));
    mocks.previewJob.mockClear();
    mocks.cancelActivity.mockClear();
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
        expect(mocks.previewJob).not.toHaveBeenCalled();
        expect(mocks.cancelActivity).not.toHaveBeenCalled();
        view.unmount();
    } finally {
        vi.useRealTimers();
    }
});
it('reports a cancelled job without returning an empty reply', async () => {
    mocks.enqueuePreviewJob.mockResolvedValue(reply('cancelled'));
    const view = renderHook(() => useQueuedDraft('42', async () => {}), {wrapper});
    await expect(view.result.current(defaultRule)).rejects.toThrow(ru.draftQueue.cancelled);
    await expect(view.result.current(defaultRule)).rejects.toMatchObject({status: 'cancelled'});
    view.unmount();
});

it('preserves the failure reason and distinguishes it from cancellation', async () => {
    mocks.enqueuePreviewJob.mockResolvedValue({...reply('error'), error: 'Модель не ответила.'});
    const view = renderHook(() => useQueuedDraft('42', async () => {}), {wrapper});
    await expect(view.result.current(defaultRule)).rejects.toMatchObject({
        status: 'error',
        message: 'Модель не ответила.',
    });
    view.unmount();
});
