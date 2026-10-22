import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {act, fireEvent, render, screen, waitFor} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {createRef} from 'react';
import {SpeechPreview} from '@/modules/speech/SpeechPreview/SpeechPreview';
import type {SpeechPreviewHandle} from '@/modules/speech/SpeechPreview/SpeechPreview';
import {generatedVoice, speechState} from '@tests/fixtures/speech';
import ru from '@/shared/i18n/ru.json';

const preview = vi.hoisted(() => ({
    run: vi.fn().mockResolvedValue(undefined),
    cancel: vi.fn(),
    busy: false,
    cancelling: false,
    error: '',
    job: undefined,
    voice: undefined as unknown,
}));
vi.mock('@/modules/speech/model/useSpeechPreview', () => ({useSpeechPreview: () => preview}));

it('hides outdated audio when unsaved text or voice changes and never exposes sending', async () => {
    vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {});
    function surface(modelId: string) {
        return (
            <NextIntlClientProvider locale="ru" messages={ru}>
                <ThemeProvider>
                    <SpeechPreview modelId={modelId} speed={1.2} ready />
                </ThemeProvider>
            </NextIntlClientProvider>
        );
    }
    const view = render(surface('first'));
    const input = screen.getByRole('textbox', {name: ru.voice.previewText});
    fireEvent.change(input, {target: {value: '  Точная фраза 🙂 '}});
    fireEvent.click(screen.getByRole('button', {name: ru.voice.previewCheck}));
    await waitFor(() =>
        expect(preview.run).toHaveBeenCalledWith({text: '  Точная фраза 🙂 ', modelId: 'first', speed: 1.2}),
    );
    preview.voice = generatedVoice;
    view.rerender(surface('first'));
    expect(view.container.querySelector('audio')).toHaveAttribute('src', generatedVoice.url);
    expect(screen.queryByRole('button', {name: ru.voice.sendDraft})).not.toBeInTheDocument();
    view.rerender(surface('second'));
    expect(view.container.querySelector('audio')).toBeNull();
    preview.voice = undefined;
    vi.restoreAllMocks();
});

it('applies the request limit in UTF-16 units, including emoji at the exact boundary', () => {
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <SpeechPreview modelId="piper-ru-irina" speed={1} ready />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    const input = screen.getByRole('textbox', {name: ru.voice.previewText});
    fireEvent.change(input, {target: {value: '🙂'.repeat(2048)}});
    expect(screen.getByRole('button', {name: ru.voice.previewCheck})).toBeEnabled();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    fireEvent.change(input, {target: {value: '🙂'.repeat(2049)}});
    expect(screen.getByRole('button', {name: ru.voice.previewCheck})).toBeDisabled();
    expect(screen.getByRole('alert')).toHaveTextContent(ru.voice.transcriptTooLong);
});

it('checks the explicit installed card voice with the current text and speed before preferences are saved', async () => {
    preview.run.mockClear();
    const ref = createRef<SpeechPreviewHandle>();
    const assets = [...speechState.assets, {...speechState.assets[2], id: 'selected-card-voice'}];
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <SpeechPreview ref={ref} modelId="piper-ru-irina" speed={1.4} ready assets={assets} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    fireEvent.change(screen.getByRole('textbox', {name: ru.voice.previewText}), {
        target: {value: '  Точная проверка 🙂 '},
    });
    await act(async () => {
        ref.current?.check('selected-card-voice');
    });
    expect(preview.run).toHaveBeenCalledExactlyOnceWith({
        text: '  Точная проверка 🙂 ',
        modelId: 'selected-card-voice',
        speed: 1.4,
    });
});

it('requires saved device preferences and invalidates old CPU audio after saving CUDA without losing text', async () => {
    preview.run.mockClear();
    preview.voice = undefined;
    vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {});
    const ref = createRef<SpeechPreviewHandle>();
    function surface(configurationKey: string, blockedReason?: string) {
        return (
            <NextIntlClientProvider locale="ru" messages={ru}>
                <ThemeProvider>
                    <SpeechPreview
                        ref={ref}
                        modelId="rvc-synthetic"
                        speed={1}
                        ready
                        configurationKey={configurationKey}
                        blockedReason={blockedReason}
                    />
                </ThemeProvider>
            </NextIntlClientProvider>
        );
    }
    const view = render(surface('cpu'));
    const input = screen.getByRole('textbox', {name: ru.voice.previewText});
    fireEvent.change(input, {target: {value: 'Keep this exact preview text 🙂'}});
    fireEvent.click(screen.getByRole('button', {name: ru.voice.previewCheck}));
    await waitFor(() => expect(preview.run).toHaveBeenCalledTimes(1));
    preview.voice = generatedVoice;
    view.rerender(surface('cpu'));
    expect(view.container.querySelector('audio')).toHaveAttribute('src', generatedVoice.url);
    view.rerender(surface('cpu', ru.voice.rvcDevice.saveBeforePreview));
    expect(screen.getByRole('button', {name: ru.voice.previewCheck})).toBeDisabled();
    expect(view.container.querySelector('audio')).toBeNull();
    await act(async () => {
        ref.current?.check('rvc-synthetic');
    });
    expect(preview.run).toHaveBeenCalledTimes(1);
    view.rerender(surface('cuda'));
    expect(screen.getByRole('textbox', {name: ru.voice.previewText})).toHaveValue(
        'Keep this exact preview text 🙂',
    );
    expect(view.container.querySelector('audio')).toBeNull();
    expect(screen.getByRole('button', {name: ru.voice.previewCheck})).toBeEnabled();
    fireEvent.click(screen.getByRole('button', {name: ru.voice.previewCheck}));
    await waitFor(() => expect(preview.run).toHaveBeenCalledTimes(2));
    expect(preview.run).toHaveBeenLastCalledWith({
        text: 'Keep this exact preview text 🙂',
        modelId: 'rvc-synthetic',
        speed: 1,
    });
    preview.voice = {...generatedVoice, id: 'gpu-preview', url: '/api/speech/audio/gpu-preview'};
    view.rerender(surface('cuda'));
    expect(view.container.querySelector('audio')).toHaveAttribute('src', '/api/speech/audio/gpu-preview');
    preview.voice = undefined;
    vi.restoreAllMocks();
});
