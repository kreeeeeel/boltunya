import {act, renderHook} from '@testing-library/react';
import {NextIntlClientProvider} from 'next-intl';
import {afterEach, beforeEach, expect, it, vi} from 'vitest';
import type {ReactNode} from 'react';
import {useSpeechPreview} from '@/modules/speech/model/useSpeechPreview';
import {generatedVoice} from '@tests/fixtures/speech';
import ru from '@/shared/i18n/ru.json';

const mocks = vi.hoisted(() => ({
    previewSpeech: vi.fn(),
    enqueueVoiceDraft: vi.fn(),
    previewJob: vi.fn(),
    cancelActivity: vi.fn(),
}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: {
        speech: {previewSpeech: mocks.previewSpeech, enqueueVoiceDraft: mocks.enqueueVoiceDraft},
        models: {previewJob: mocks.previewJob},
        activity: {cancelActivity: mocks.cancelActivity},
    },
}));

function wrapper({children}: {readonly children: ReactNode}) {
    return (
        <NextIntlClientProvider locale="ru" messages={ru}>
            {children}
        </NextIntlClientProvider>
    );
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
});
afterEach(() => vi.useRealTimers());

it('queues exact text with the unsaved voice and speed, then reads real audio from the shared job', async () => {
    mocks.previewSpeech.mockResolvedValue({id: 'preview-1', status: 'queued'});
    mocks.previewJob.mockResolvedValue({
        id: 'preview-1',
        status: 'ready',
        result: {text: '  Привет 🙂 ', voice: generatedVoice},
    });
    const view = renderHook(() => useSpeechPreview(), {wrapper});
    const request = {text: '  Привет 🙂 ', modelId: 'custom-voice', speed: 1.3};
    await act(async () => {
        await view.result.current.run(request);
    });
    expect(mocks.previewSpeech).toHaveBeenCalledExactlyOnceWith(
        {speechPreviewRequest: request},
        {signal: expect.any(AbortSignal)},
    );
    expect(view.result.current.busy).toBe(true);
    expect(view.result.current.voice).toBeUndefined();
    await act(async () => {
        await vi.advanceTimersByTimeAsync(750);
    });
    expect(mocks.previewJob).toHaveBeenCalledExactlyOnceWith(
        {id: 'preview-1'},
        {signal: expect.any(AbortSignal)},
    );
    expect(view.result.current.voice).toEqual(generatedVoice);
    expect(view.result.current.busy).toBe(false);
    view.unmount();
});

it('cancels through the shared activity queue and stops polling on unmount', async () => {
    mocks.previewSpeech.mockResolvedValue({id: 'preview-2', status: 'generating'});
    mocks.cancelActivity.mockResolvedValue({ok: true});
    mocks.previewJob.mockResolvedValue({id: 'preview-2', status: 'cancelled'});
    const view = renderHook(() => useSpeechPreview(), {wrapper});
    await act(async () => {
        await view.result.current.run({text: 'Текст', modelId: 'piper-ru-irina', speed: 1});
    });
    await act(async () => {
        await view.result.current.cancel();
    });
    expect(mocks.cancelActivity).toHaveBeenCalledExactlyOnceWith({id: 'preview-2'});
    await act(async () => {
        await vi.advanceTimersByTimeAsync(750);
    });
    expect(view.result.current.job?.status).toBe('cancelled');
    expect(view.result.current.voice).toBeUndefined();
    view.unmount();
    await act(async () => {
        await vi.advanceTimersByTimeAsync(3000);
    });
    expect(mocks.previewJob).toHaveBeenCalledOnce();
});

it('cancels a still-running shared job when its settings panel unmounts', async () => {
    mocks.previewSpeech.mockResolvedValue({id: 'preview-unmount', status: 'queued'});
    mocks.cancelActivity.mockResolvedValue({ok: true});
    const view = renderHook(() => useSpeechPreview(), {wrapper});
    await act(async () => {
        await view.result.current.run({text: 'Текст', modelId: 'piper-ru-irina', speed: 1});
    });
    view.unmount();
    expect(mocks.cancelActivity).toHaveBeenCalledExactlyOnceWith({id: 'preview-unmount'});
    await act(async () => {
        await vi.advanceTimersByTimeAsync(3000);
    });
    expect(mocks.previewJob).not.toHaveBeenCalled();
});

it('waits for cancellation of a known job after polling fails before queuing another preview', async () => {
    const cancellation = Promise.withResolvers<{ok: boolean}>();
    mocks.previewSpeech.mockResolvedValueOnce({id: 'old-preview', status: 'queued'}).mockResolvedValueOnce({
        id: 'new-preview',
        status: 'ready',
        result: {text: 'Новый текст', voice: generatedVoice},
    });
    mocks.previewJob.mockRejectedValue(new Error('Polling connection lost'));
    mocks.cancelActivity.mockReturnValue(cancellation.promise);
    const view = renderHook(() => useSpeechPreview(), {wrapper});
    await act(async () => {
        await view.result.current.run({text: 'Текст', modelId: 'piper-ru-irina', speed: 1});
    });
    await act(async () => {
        await vi.advanceTimersByTimeAsync(750);
    });
    expect(view.result.current.error).toBe('Polling connection lost');
    expect(view.result.current.busy).toBe(false);
    let next: Promise<void> | undefined;
    await act(async () => {
        next = view.result.current.run({text: 'Новый текст', modelId: 'piper-ru-irina', speed: 1});
    });
    expect(mocks.cancelActivity).toHaveBeenCalledExactlyOnceWith({id: 'old-preview'});
    expect(mocks.previewSpeech).toHaveBeenCalledOnce();
    expect(view.result.current.busy).toBe(true);
    await act(async () => {
        cancellation.resolve({ok: true});
        await next;
    });
    expect(mocks.previewSpeech).toHaveBeenCalledTimes(2);
    expect(view.result.current.voice).toEqual(generatedVoice);
    view.unmount();
    expect(mocks.cancelActivity).toHaveBeenCalledOnce();
});

it('preserves the known job and blocks another enqueue when cancellation fails', async () => {
    mocks.previewSpeech.mockResolvedValue({id: 'retained-preview', status: 'generating'});
    mocks.previewJob.mockRejectedValue(new Error('Polling connection lost'));
    mocks.cancelActivity
        .mockRejectedValueOnce(new Error('Cancellation failed'))
        .mockResolvedValue({ok: true});
    const view = renderHook(() => useSpeechPreview(), {wrapper});
    const request = {text: 'Текст', modelId: 'piper-ru-irina', speed: 1};
    await act(async () => {
        await view.result.current.run(request);
    });
    await act(async () => {
        await vi.advanceTimersByTimeAsync(750);
    });
    await act(async () => {
        await view.result.current.run(request);
    });
    expect(view.result.current.error).toBe('Cancellation failed');
    expect(view.result.current.busy).toBe(false);
    expect(view.result.current.job?.id).toBe('retained-preview');
    expect(mocks.previewSpeech).toHaveBeenCalledOnce();
    view.unmount();
    expect(mocks.cancelActivity).toHaveBeenCalledTimes(2);
    expect(mocks.cancelActivity).toHaveBeenLastCalledWith({id: 'retained-preview'});
});

it('shares an in-flight cancellation with unmount and does not enqueue after closing', async () => {
    const cancellation = Promise.withResolvers<{ok: boolean}>();
    mocks.previewSpeech.mockResolvedValue({id: 'closing-preview', status: 'queued'});
    mocks.previewJob.mockRejectedValue(new Error('Polling connection lost'));
    mocks.cancelActivity.mockReturnValue(cancellation.promise);
    const view = renderHook(() => useSpeechPreview(), {wrapper});
    const request = {text: 'Текст', modelId: 'piper-ru-irina', speed: 1};
    await act(async () => {
        await view.result.current.run(request);
    });
    await act(async () => {
        await vi.advanceTimersByTimeAsync(750);
    });
    let next: Promise<void> | undefined;
    await act(async () => {
        next = view.result.current.run(request);
    });
    view.unmount();
    expect(mocks.cancelActivity).toHaveBeenCalledExactlyOnceWith({id: 'closing-preview'});
    await act(async () => {
        cancellation.resolve({ok: true});
        await next;
    });
    expect(mocks.previewSpeech).toHaveBeenCalledOnce();
});

it('queues chat-bound manual speech with exact per-request voice and speed without an LLM or global save', async () => {
    mocks.enqueueVoiceDraft.mockResolvedValue({
        id: 'chat-voice',
        status: 'ready',
        result: {text: '  Мой текст 🙂 ', voice: generatedVoice},
    });
    const view = renderHook(() => useSpeechPreview('42'), {wrapper});
    await act(async () => {
        await view.result.current.run({text: '  Мой текст 🙂 ', modelId: 'custom-voice', speed: 1.3});
    });
    expect(mocks.enqueueVoiceDraft).toHaveBeenCalledExactlyOnceWith(
        {voiceDraftRequest: {chatId: '42', text: '  Мой текст 🙂 ', modelId: 'custom-voice', speed: 1.3}},
        {signal: expect.any(AbortSignal)},
    );
    expect(mocks.previewSpeech).not.toHaveBeenCalled();
    expect(view.result.current.voice).toEqual(generatedVoice);
    view.unmount();
});
