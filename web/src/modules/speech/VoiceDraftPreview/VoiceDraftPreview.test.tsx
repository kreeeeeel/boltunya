import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {cleanup, fireEvent, render, screen, waitFor} from '@testing-library/react';
import {afterEach, beforeEach, expect, it, vi} from 'vitest';
import {VoiceDraftPreview} from '@/modules/speech/VoiceDraftPreview/VoiceDraftPreview';
import {generatedVoice} from '@tests/fixtures/speech';
import ru from '@/shared/i18n/ru.json';

beforeEach(() => {
    vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {});
});
afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
});

it('invalidates old audio after editing, queues exact transcript and sends only refreshed voice', async () => {
    const send = vi.fn().mockResolvedValue(undefined);
    const discard = vi.fn();
    const synthesize = vi.fn().mockResolvedValue({
        text: '  Новый текст 🙂 ',
        voice: {...generatedVoice, id: 'new-voice'},
        durationMs: 100,
        tokensPerSecond: 0,
    });
    const {container} = render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <VoiceDraftPreview
                    draft={{text: 'Первый текст', voice: generatedVoice, durationMs: 100, tokensPerSecond: 0}}
                    onSynthesize={synthesize}
                    onSend={send}
                    onDiscard={discard}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    expect(container.querySelector('audio')).toHaveAttribute('src', generatedVoice.url);
    expect(container.querySelector('audio')).toHaveAttribute('hidden');
    fireEvent.change(screen.getByRole('textbox', {name: ru.voice.transcript}), {
        target: {value: '  Новый текст 🙂 '},
    });
    expect(container.querySelector('audio')).toBeNull();
    expect(screen.getByRole('button', {name: ru.voice.sendDraft})).toBeDisabled();
    expect(screen.getByRole('status')).toHaveTextContent(ru.voice.transcriptChanged);
    fireEvent.click(screen.getByRole('button', {name: ru.voice.regenerate}));
    await waitFor(() => expect(synthesize).toHaveBeenCalledExactlyOnceWith('  Новый текст 🙂 '));
    await waitFor(() => expect(screen.getByRole('button', {name: ru.voice.sendDraft})).toBeEnabled());
    fireEvent.click(screen.getByRole('button', {name: ru.voice.sendDraft}));
    await waitFor(() => expect(send).toHaveBeenCalledExactlyOnceWith('new-voice'));
    expect(discard).toHaveBeenCalledOnce();
});

it('preserves the transcript and blocks outdated audio when synthesis fails', async () => {
    const send = vi.fn();
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <VoiceDraftPreview
                    draft={{text: 'Исходный текст', voice: generatedVoice, durationMs: 0, tokensPerSecond: 0}}
                    onSynthesize={vi.fn().mockRejectedValue(new Error('Synthesis failed'))}
                    onSend={send}
                    onDiscard={vi.fn()}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    fireEvent.change(screen.getByRole('textbox', {name: ru.voice.transcript}), {
        target: {value: 'Мой текст'},
    });
    fireEvent.click(screen.getByRole('button', {name: ru.voice.regenerate}));
    expect(await screen.findByRole('alert')).toHaveTextContent('Synthesis failed');
    expect(screen.getByRole('textbox', {name: ru.voice.transcript})).toHaveValue('Мой текст');
    expect(screen.getByRole('button', {name: ru.voice.sendDraft})).toBeDisabled();
    expect(send).not.toHaveBeenCalled();
});

it('counts emoji as two UTF-16 units for the synthesis request boundary', () => {
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <VoiceDraftPreview
                    draft={{text: 'Исходный текст', voice: generatedVoice, durationMs: 0, tokensPerSecond: 0}}
                    onSynthesize={vi.fn()}
                    onSend={vi.fn()}
                    onDiscard={vi.fn()}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    const input = screen.getByRole('textbox', {name: ru.voice.transcript});
    fireEvent.change(input, {target: {value: '🙂'.repeat(2048)}});
    expect(screen.getByRole('button', {name: ru.voice.regenerate})).toBeEnabled();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    fireEvent.change(input, {target: {value: '🙂'.repeat(2049)}});
    expect(screen.getByRole('button', {name: ru.voice.regenerate})).toBeDisabled();
    expect(screen.getByRole('button', {name: ru.voice.sendDraft})).toBeDisabled();
    expect(screen.getByRole('alert')).toHaveTextContent(ru.voice.transcriptTooLong);
});
