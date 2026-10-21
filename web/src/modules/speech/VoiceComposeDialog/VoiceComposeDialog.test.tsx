import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {act, fireEvent, render, screen, waitFor} from '@testing-library/react';
import {afterEach, beforeEach, expect, it, vi} from 'vitest';
import {VoiceComposeDialog} from '@/modules/speech/VoiceComposeDialog/VoiceComposeDialog';
import {generatedVoice, speechState} from '@tests/fixtures/speech';
import ru from '@/shared/i18n/ru.json';

const mocks = vi.hoisted(() => ({
    speechState: vi.fn(),
    enqueueVoiceDraft: vi.fn(),
    sendVoiceDraft: vi.fn(),
    previewJob: vi.fn(),
    cancelActivity: vi.fn(),
}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: {
        speech: {
            speechState: mocks.speechState,
            enqueueVoiceDraft: mocks.enqueueVoiceDraft,
            sendVoiceDraft: mocks.sendVoiceDraft,
        },
        models: {previewJob: mocks.previewJob},
        activity: {cancelActivity: mocks.cancelActivity},
    },
}));

beforeEach(() => {
    vi.clearAllMocks();
    mocks.speechState.mockResolvedValue({
        ...speechState,
        assets: [
            ...speechState.assets,
            {...speechState.assets[2], id: 'second-voice', title: 'Второй голос'},
        ],
    });
    mocks.enqueueVoiceDraft.mockResolvedValue({
        id: 'manual-job',
        status: 'ready',
        result: {text: '  Мой текст 🙂 ', voice: generatedVoice},
    });
    mocks.sendVoiceDraft.mockResolvedValue({ok: true});
    mocks.cancelActivity.mockResolvedValue({ok: true});
    vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {});
});
afterEach(() => vi.restoreAllMocks());

function setup(onSent = vi.fn().mockResolvedValue(undefined)) {
    const close = vi.fn();
    const view = render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <VoiceComposeDialog
                    chatId="42"
                    defaultModelId="piper-ru-irina"
                    defaultSpeed={1.2}
                    onClose={close}
                    onSent={onSent}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    return {...view, close, onSent};
}

it('synthesizes exact text with an installed per-request voice and speed without saving settings', async () => {
    setup();
    await waitFor(() =>
        expect(screen.getByRole('combobox', {name: ru.voice.models.synthesis})).toBeEnabled(),
    );
    expect(screen.getByRole('button', {name: ru.voice.previewCheck})).toBeDisabled();
    fireEvent.click(screen.getByRole('combobox', {name: ru.voice.models.synthesis}));
    fireEvent.click(await screen.findByRole('option', {name: 'Второй голос'}));
    fireEvent.change(screen.getByRole('textbox', {name: ru.voice.manualText}), {
        target: {value: '  Мой текст 🙂 '},
    });
    fireEvent.click(screen.getByRole('button', {name: ru.voice.previewCheck}));
    await waitFor(() =>
        expect(
            screen.getByRole('dialog', {name: ru.voice.manualTitle}).querySelector('audio'),
        ).toHaveAttribute('src', generatedVoice.url),
    );
    expect(mocks.enqueueVoiceDraft).toHaveBeenCalledExactlyOnceWith(
        {voiceDraftRequest: {chatId: '42', text: '  Мой текст 🙂 ', modelId: 'second-voice', speed: 1.2}},
        {signal: expect.any(AbortSignal)},
    );
    expect(mocks.sendVoiceDraft).not.toHaveBeenCalled();
});

it('invalidates edited audio and sends a confirmed voice only once even when refreshing the chat fails', async () => {
    const refreshed = vi.fn().mockRejectedValue(new Error('Refresh connection lost'));
    setup(refreshed);
    await waitFor(() =>
        expect(screen.getByRole('combobox', {name: ru.voice.models.synthesis})).toBeEnabled(),
    );
    const input = screen.getByRole('textbox', {name: ru.voice.manualText});
    fireEvent.change(input, {target: {value: '  Мой текст 🙂 '}});
    fireEvent.click(screen.getByRole('button', {name: ru.voice.previewCheck}));
    await waitFor(() =>
        expect(
            screen.getByRole('dialog', {name: ru.voice.manualTitle}).querySelector('audio'),
        ).toHaveAttribute('src', generatedVoice.url),
    );
    fireEvent.change(input, {target: {value: 'Другой текст'}});
    expect(screen.getByRole('dialog', {name: ru.voice.manualTitle}).querySelector('audio')).toBeNull();
    expect(screen.getByRole('button', {name: ru.voice.manualSpeakAndSend})).toBeEnabled();
    fireEvent.change(input, {target: {value: '  Мой текст 🙂 '}});
    const send = screen.getByRole('button', {name: ru.voice.manualSpeakAndSend});
    await act(async () => {
        fireEvent.click(send);
        fireEvent.click(send);
    });
    expect(await screen.findByRole('alert')).toHaveTextContent(ru.voice.manualRefreshFailed);
    expect(screen.getByRole('button', {name: ru.voice.manualSent})).toBeDisabled();
    fireEvent.click(screen.getByRole('button', {name: ru.voice.manualSent}));
    expect(mocks.sendVoiceDraft).toHaveBeenCalledExactlyOnceWith({
        chatId: '42',
        voiceSendRequest: {voiceId: generatedVoice.id},
    });
    expect(refreshed).toHaveBeenCalledOnce();
    expect(input).toHaveValue('  Мой текст 🙂 ');
    refreshed.mockResolvedValueOnce(undefined);
    fireEvent.click(screen.getByRole('button', {name: ru.voice.manualRefresh}));
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
    expect(refreshed).toHaveBeenCalledTimes(2);
    expect(mocks.sendVoiceDraft).toHaveBeenCalledOnce();
});

it('synthesizes and sends exact text in one explicit action without requiring a preview first', async () => {
    setup();
    await waitFor(() =>
        expect(screen.getByRole('combobox', {name: ru.voice.models.synthesis})).toBeEnabled(),
    );
    fireEvent.change(screen.getByRole('textbox', {name: ru.voice.manualText}), {
        target: {value: '  Мой текст 🙂 '},
    });
    await act(async () => {
        const primary = screen.getByRole('button', {name: ru.voice.manualSpeakAndSend});
        fireEvent.click(primary);
        fireEvent.click(primary);
    });
    await waitFor(() => expect(screen.getByRole('button', {name: ru.voice.manualSent})).toBeDisabled());
    expect(mocks.enqueueVoiceDraft).toHaveBeenCalledExactlyOnceWith(
        {voiceDraftRequest: {chatId: '42', text: '  Мой текст 🙂 ', modelId: 'piper-ru-irina', speed: 1.2}},
        {signal: expect.any(AbortSignal)},
    );
    expect(mocks.sendVoiceDraft).toHaveBeenCalledExactlyOnceWith({
        chatId: '42',
        voiceSendRequest: {voiceId: generatedVoice.id},
    });
});

it('cancels the intent on Close so an already enqueued result cannot be sent afterward', async () => {
    vi.useFakeTimers();
    try {
        mocks.enqueueVoiceDraft.mockResolvedValue({id: 'pending-voice', status: 'queued'});
        mocks.previewJob.mockResolvedValue({
            id: 'pending-voice',
            status: 'ready',
            result: {text: 'Текст', voice: generatedVoice},
        });
        const view = setup();
        await act(async () => {
            await vi.advanceTimersByTimeAsync(0);
        });
        fireEvent.change(screen.getByRole('textbox', {name: ru.voice.manualText}), {
            target: {value: 'Текст'},
        });
        await act(async () => {
            fireEvent.click(screen.getByRole('button', {name: ru.voice.manualSpeakAndSend}));
        });
        fireEvent.click(screen.getByRole('button', {name: ru.close}));
        expect(view.close).toHaveBeenCalledOnce();
        await act(async () => {
            await vi.advanceTimersByTimeAsync(750);
        });
        expect(mocks.sendVoiceDraft).not.toHaveBeenCalled();
        view.unmount();
    } finally {
        vi.useRealTimers();
    }
});

it('blocks retry of an ambiguously sent voice until new audio is explicitly generated', async () => {
    mocks.sendVoiceDraft.mockRejectedValueOnce(new Error('Timed out'));
    setup();
    await waitFor(() =>
        expect(screen.getByRole('combobox', {name: ru.voice.models.synthesis})).toBeEnabled(),
    );
    fireEvent.change(screen.getByRole('textbox', {name: ru.voice.manualText}), {
        target: {value: '  Мой текст 🙂 '},
    });
    fireEvent.click(screen.getByRole('button', {name: ru.voice.previewCheck}));
    await waitFor(() =>
        expect(screen.getByRole('button', {name: ru.voice.manualSpeakAndSend})).toBeEnabled(),
    );
    fireEvent.click(screen.getByRole('button', {name: ru.voice.manualSpeakAndSend}));
    expect(await screen.findByRole('alert')).toHaveTextContent(ru.voice.manualSendUnconfirmed);
    expect(screen.getByRole('button', {name: ru.voice.manualSpeakAndSend})).toBeDisabled();
    mocks.enqueueVoiceDraft.mockResolvedValueOnce({
        id: 'new-job',
        status: 'ready',
        result: {text: '  Мой текст 🙂 ', voice: {...generatedVoice, id: 'new-voice'}},
    });
    fireEvent.click(screen.getByRole('button', {name: ru.voice.previewCheck}));
    await waitFor(() =>
        expect(screen.getByRole('button', {name: ru.voice.manualSpeakAndSend})).toBeEnabled(),
    );
    expect(mocks.sendVoiceDraft).toHaveBeenCalledOnce();
});

it('cancels automatic-send intent even if a ready result races with accepted cancellation', async () => {
    vi.useFakeTimers();
    try {
        mocks.enqueueVoiceDraft.mockResolvedValue({id: 'cancel-race', status: 'queued'});
        mocks.previewJob.mockResolvedValue({
            id: 'cancel-race',
            status: 'ready',
            result: {text: 'Текст', voice: generatedVoice},
        });
        const view = setup();
        await act(async () => {
            await vi.advanceTimersByTimeAsync(0);
        });
        fireEvent.change(screen.getByRole('textbox', {name: ru.voice.manualText}), {
            target: {value: 'Текст'},
        });
        await act(async () => {
            fireEvent.click(screen.getByRole('button', {name: ru.voice.manualSpeakAndSend}));
        });
        await act(async () => {
            fireEvent.click(screen.getByRole('button', {name: ru.voice.cancel}));
        });
        expect(mocks.cancelActivity).toHaveBeenCalledExactlyOnceWith({id: 'cancel-race'});
        await act(async () => {
            await vi.advanceTimersByTimeAsync(750);
        });
        expect(mocks.sendVoiceDraft).not.toHaveBeenCalled();
        view.unmount();
    } finally {
        vi.useRealTimers();
    }
});

it('requires installed runtime dependencies and enforces the same UTF-16 text limit as the API', async () => {
    mocks.speechState.mockResolvedValue({
        ...speechState,
        assets: speechState.assets.map((asset) => ({...asset, installed: asset.id !== 'sherpa-runtime'})),
    });
    const view = setup();
    await waitFor(() =>
        expect(screen.getByRole('combobox', {name: ru.voice.models.synthesis})).toBeEnabled(),
    );
    fireEvent.change(screen.getByRole('textbox', {name: ru.voice.manualText}), {target: {value: 'Текст'}});
    expect(screen.getByRole('button', {name: ru.voice.previewCheck})).toBeDisabled();
    expect(mocks.enqueueVoiceDraft).not.toHaveBeenCalled();
    view.unmount();
    mocks.speechState.mockResolvedValue(speechState);
    setup();
    await waitFor(() =>
        expect(screen.getByRole('combobox', {name: ru.voice.models.synthesis})).toBeEnabled(),
    );
    const text = screen.getByRole('textbox', {name: ru.voice.manualText});
    fireEvent.change(text, {target: {value: '🙂'.repeat(2048)}});
    expect(screen.getByRole('button', {name: ru.voice.previewCheck})).toBeEnabled();
    fireEvent.change(text, {target: {value: '🙂'.repeat(2049)}});
    expect(screen.getByRole('button', {name: ru.voice.previewCheck})).toBeDisabled();
    expect(screen.getByRole('alert')).toHaveTextContent(ru.voice.transcriptTooLong);
});
