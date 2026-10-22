import {Box, ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {ConversationAssistantBar} from '@/modules/workspace/Conversation/ConversationAssistantBar';
import {speechState} from '@tests/fixtures/speech';
import ru from '@/shared/i18n/ru.json';

vi.mock('@/modules/speech/model/useSpeechState', () => ({useSpeechState: () => ({state: speechState})}));
vi.mock('@/modules/speech/VoiceComposeDialog/VoiceComposeDialog', () => ({
    VoiceComposeDialog: ({defaultModelId, defaultSpeed}: {defaultModelId: string; defaultSpeed: number}) => (
        <Box role="dialog" aria-label="Manual voice">
            {defaultModelId} · {defaultSpeed}
        </Box>
    ),
}));

function mount(busy = false) {
    const draft = vi.fn().mockResolvedValue(undefined);
    const memory = vi.fn();
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ConversationAssistantBar
                    chatId="42"
                    modelLabel="Synthetic model"
                    busy={busy}
                    onDraft={draft}
                    onMemory={memory}
                    onMediaSent={vi.fn()}
                    voiceSynthesisModel="piper-ru-irina"
                    voiceSpeechRate={1.4}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    return {draft, memory};
}

it('shows the current model and explicit assistant actions without a manual composer or attachment controls', () => {
    const {memory} = mount();
    expect(screen.getByText(ru.assistantBar.modelCaption)).toBeVisible();
    expect(screen.getByText('Synthetic model')).toBeVisible();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(document.querySelector('input[type="file"]')).toBeNull();
    expect(screen.queryByRole('button', {name: ru.chooseEmoji})).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', {name: ru.memory.openChat}));
    expect(memory).toHaveBeenCalledOnce();
    fireEvent.click(screen.getByRole('button', {name: ru.voice.manualTitle}));
    expect(screen.getByRole('dialog', {name: 'Manual voice'})).toHaveTextContent('piper-ru-irina · 1.4');
});

it.each(['text', 'voice'] as const)(
    'generates an explicit %s draft from the assistant menu',
    async (output) => {
        const {draft} = mount();
        fireEvent.click(screen.getByRole('button', {name: ru.draft}));
        fireEvent.click(
            await screen.findByRole('menuitem', {
                name: output === 'text' ? ru.voice.generateText : ru.voice.generateVoice,
            }),
        );
        expect(draft).toHaveBeenCalledExactlyOnceWith(output);
    },
);

it('blocks new generation and manual speech while another send or draft is pending', () => {
    mount(true);
    expect(screen.getByRole('button', {name: ru.draft})).toBeDisabled();
    expect(screen.getByRole('button', {name: ru.voice.manualTitle})).toBeDisabled();
    expect(screen.getByRole('button', {name: ru.memory.openChat})).toBeEnabled();
});
