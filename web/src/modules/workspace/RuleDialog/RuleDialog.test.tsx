import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {expect, it, vi} from 'vitest';

import {RuleDialog} from '@/modules/workspace/RuleDialog/RuleDialog';
import {workspaceState} from '@tests/fixtures/workspace';
import ru from '@/shared/i18n/ru.json';
import {speechState} from '@tests/fixtures/speech';

const preset = {id: 'calm', emoji: '🧊', name: 'Спокойный', instructions: 'Коротко, спокойно и на ты.'};
vi.mock('@/modules/settings/model/useModelProfiles', () => ({
    useModelProfiles: () => ({profiles: [], presets: [], error: '', loading: false, refresh: vi.fn()}),
}));
vi.mock('@/modules/speech/model/useSpeechState', () => ({
    useSpeechState: () => ({state: speechState, error: '', reload: vi.fn()}),
}));
vi.mock('@/modules/settings/model/usePromptBudget', () => ({
    usePromptBudget: () => ({report: undefined, error: '', loading: true}),
}));

it('offers named presets without the standalone custom style and saves the reference with personal instructions', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    const chat = structuredClone(workspaceState.chats[0]);
    chat.rule.instructions = 'Не обещай встречи.';
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <RuleDialog
                    chat={chat}
                    customStyles={[preset]}
                    engine={workspaceState.engine}
                    defaultModel={workspaceState.settings.defaultModel}
                    onClose={vi.fn()}
                    onCollectContext={vi.fn()}
                    onDeleted={vi.fn()}
                    onSave={onSave}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    fireEvent.click(screen.getByRole('menuitem', {name: ru.chatSettings.sections.tone}));
    fireEvent.click(screen.getByRole('combobox', {name: ru.style}));
    expect(screen.queryByRole('option', {name: ru.custom})).toBeNull();
    fireEvent.click(await screen.findByRole('option', {name: (name) => name.includes('Спокойный')}));
    fireEvent.click(screen.getByRole('button', {name: ru.save}));
    await waitFor(() =>
        expect(onSave).toHaveBeenCalledWith(
            expect.objectContaining({
                style: 'custom',
                styleId: preset.id,
                instructions: 'Не обещай встречи.',
            }),
        ),
    );
    expect(onSave.mock.calls[0][0].instructions).not.toContain(preset.instructions);
});

it('keeps legacy custom instructions while replacing a missing preset with a concrete style', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    const chat = structuredClone(workspaceState.chats[0]);
    chat.rule = {...chat.rule, style: 'custom', styleId: '', instructions: 'Без длинных ответов.'};
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <RuleDialog
                    chat={chat}
                    customStyles={[]}
                    engine={workspaceState.engine}
                    defaultModel={workspaceState.settings.defaultModel}
                    onClose={vi.fn()}
                    onCollectContext={vi.fn()}
                    onDeleted={vi.fn()}
                    onSave={onSave}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    fireEvent.click(screen.getByRole('menuitem', {name: ru.chatSettings.sections.tone}));
    expect(screen.getByRole('combobox', {name: ru.style})).toHaveTextContent(ru.friendly);
    fireEvent.click(screen.getByRole('button', {name: ru.save}));
    await waitFor(() =>
        expect(onSave).toHaveBeenCalledWith(
            expect.objectContaining({style: 'friendly', styleId: '', instructions: 'Без длинных ответов.'}),
        ),
    );
});

it('saves the proactive mode and a chat-specific interval without closing the dialog', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    const onClose = vi.fn();
    const chat = structuredClone(workspaceState.chats[0]);
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <RuleDialog
                    chat={chat}
                    customStyles={[]}
                    engine={workspaceState.engine}
                    defaultModel={workspaceState.settings.defaultModel}
                    onClose={onClose}
                    onCollectContext={vi.fn()}
                    onDeleted={vi.fn()}
                    onSave={onSave}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    fireEvent.click(screen.getByRole('menuitem', {name: ru.chatSettings.sections.proactive}));
    fireEvent.click(screen.getByRole('combobox', {name: ru.proactive.mode}));
    fireEvent.click(await screen.findByRole('option', {name: ru.proactive.modes.enabled}));
    fireEvent.click(screen.getByRole('switch', {name: ru.proactive.inheritInterval}));
    fireEvent.change(screen.getByRole('spinbutton', {name: ru.proactive.interval}), {target: {value: '2'}});
    fireEvent.click(screen.getByRole('button', {name: ru.save}));
    await waitFor(() =>
        expect(onSave).toHaveBeenCalledWith(
            expect.objectContaining({proactiveMode: 'enabled', proactiveIntervalMinutes: 120}),
        ),
    );
    expect(onClose).not.toHaveBeenCalled();
});

it('configures a fixed initiative text without enabling automatic replies', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    const chat = structuredClone(workspaceState.chats[0]);
    chat.rule.enabled = false;
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <RuleDialog
                    chat={chat}
                    customStyles={[]}
                    engine={workspaceState.engine}
                    defaultModel={workspaceState.settings.defaultModel}
                    onClose={vi.fn()}
                    onCollectContext={vi.fn()}
                    onDeleted={vi.fn()}
                    onSave={onSave}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    fireEvent.click(screen.getByRole('menuitem', {name: ru.chatSettings.sections.proactive}));
    expect(screen.queryByRole('textbox', {name: ru.proactive.messageText})).toBeNull();
    fireEvent.click(screen.getByRole('combobox', {name: ru.proactive.messageMode}));
    fireEvent.click(await screen.findByRole('option', {name: ru.proactive.messageModes.text}));
    fireEvent.change(screen.getByRole('textbox', {name: ru.proactive.messageText}), {
        target: {value: 'Как прошёл день?'},
    });
    fireEvent.click(screen.getByRole('combobox', {name: ru.proactive.mode}));
    fireEvent.click(await screen.findByRole('option', {name: ru.proactive.modes.enabled}));
    fireEvent.click(screen.getByRole('menuitem', {name: ru.chatSettings.sections.replies}));
    expect(screen.getByRole('switch', {name: ru.enableAi})).not.toBeChecked();
    fireEvent.click(screen.getByRole('button', {name: ru.save}));
    await waitFor(() =>
        expect(onSave).toHaveBeenCalledWith(
            expect.objectContaining({
                enabled: false,
                proactiveMode: 'enabled',
                proactiveMessageMode: 'text',
                proactiveText: 'Как прошёл день?',
            }),
        ),
    );
});

it.each(['finish', 'each'] as const)(
    'saves the %s interruption policy without enabling automatic replies',
    async (mode) => {
        const onSave = vi.fn().mockResolvedValue(undefined);
        const chat = structuredClone(workspaceState.chats[0]);
        chat.rule.enabled = false;
        render(
            <NextIntlClientProvider locale="ru" messages={ru}>
                <ThemeProvider>
                    <RuleDialog
                        chat={chat}
                        customStyles={[]}
                        engine={workspaceState.engine}
                        defaultModel={workspaceState.settings.defaultModel}
                        onClose={vi.fn()}
                        onCollectContext={vi.fn()}
                        onDeleted={vi.fn()}
                        onSave={onSave}
                    />
                </ThemeProvider>
            </NextIntlClientProvider>,
        );
        expect(screen.getByRole('combobox', {name: ru.replyInterruption.mode})).toHaveTextContent(
            ru.replyInterruption.options.inherit,
        );
        fireEvent.click(screen.getByRole('combobox', {name: ru.replyInterruption.mode}));
        fireEvent.click(await screen.findByRole('option', {name: ru.replyInterruption.options[mode]}));
        expect(screen.getByRole('switch', {name: ru.enableAi})).not.toBeChecked();
        fireEvent.click(screen.getByRole('button', {name: ru.save}));
        await waitFor(() =>
            expect(onSave).toHaveBeenCalledWith(
                expect.objectContaining({enabled: false, replyInterruptionMode: mode}),
            ),
        );
    },
);

it('keeps drafts across sections and saves per-chat speech preferences without closing or enabling replies', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    const onClose = vi.fn();
    const chat = structuredClone(workspaceState.chats[0]);
    chat.rule.enabled = false;
    const settings = {
        ...workspaceState.settings,
        voiceReplyMode: 'random' as const,
        voiceReplyProbability: 73,
        voiceRecognitionModel: 'whisper-tiny',
        voiceSynthesisModel: 'piper-ru-irina',
        voiceSpeechRate: 1.4,
        voiceInputEnabled: true,
    };
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <RuleDialog
                    chat={chat}
                    settings={settings}
                    customStyles={[]}
                    engine={workspaceState.engine}
                    defaultModel={settings.defaultModel}
                    onClose={onClose}
                    onCollectContext={vi.fn()}
                    onDeleted={vi.fn()}
                    onSave={onSave}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );

    fireEvent.click(screen.getByRole('menuitem', {name: ru.chatSettings.sections.model}));
    fireEvent.change(screen.getByRole('textbox', {name: ru.instructions}), {
        target: {value: 'Только для этой беседы.'},
    });
    fireEvent.click(screen.getByRole('menuitem', {name: ru.chatSettings.sections.voice}));
    const source = screen.getByRole('combobox', {name: ru.chatSettings.voice.source});
    expect(source).toHaveTextContent(ru.chatSettings.voice.inherit);
    expect(screen.queryByRole('combobox', {name: ru.voice.models.synthesis})).not.toBeInTheDocument();
    fireEvent.click(source);
    fireEvent.click(await screen.findByRole('option', {name: ru.chatSettings.voice.override}));
    expect(screen.getByRole('combobox', {name: ru.voice.replyMode})).toHaveTextContent(
        ru.voice.replyModes.random,
    );
    expect(screen.getByRole('slider', {name: ru.voice.probability})).toHaveAttribute('aria-valuenow', '73');
    fireEvent.keyDown(screen.getByRole('slider', {name: ru.voice.probability}), {
        key: 'End',
        code: 'End',
        keyCode: 35,
        which: 35,
    });
    fireEvent.keyDown(screen.getByRole('slider', {name: ru.voice.speechRate}), {
        key: 'Home',
        code: 'Home',
        keyCode: 36,
        which: 36,
    });
    fireEvent.click(screen.getByRole('menuitem', {name: ru.chatSettings.sections.model}));
    expect(screen.getByRole('textbox', {name: ru.instructions})).toHaveValue('Только для этой беседы.');
    fireEvent.click(screen.getByRole('button', {name: ru.save}));
    await waitFor(() =>
        expect(onSave).toHaveBeenCalledWith(
            expect.objectContaining({
                enabled: false,
                instructions: 'Только для этой беседы.',
                voiceSettingsMode: 'override',
                voiceReplyMode: 'random',
                voiceReplyProbability: 100,
                voiceSpeechRate: 0.5,
                voiceInputEnabled: true,
                voiceSynthesisModel: settings.voiceSynthesisModel,
                voiceRecognitionModel: settings.voiceRecognitionModel,
            }),
        ),
    );
    expect(await screen.findByRole('button', {name: ru.saved})).toBeVisible();
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByRole('dialog')).toBeVisible();
});

it('returns to inherited speech settings without erasing the saved chat override or showing group controls in a private chat', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    const chat = structuredClone(workspaceState.chats[0]);
    chat.rule = {...chat.rule, voiceSettingsMode: 'override', voiceReplyMode: 'always', voiceSpeechRate: 1.7};
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <RuleDialog
                    chat={chat}
                    settings={workspaceState.settings}
                    customStyles={[]}
                    engine={workspaceState.engine}
                    defaultModel={workspaceState.settings.defaultModel}
                    onClose={vi.fn()}
                    onCollectContext={vi.fn()}
                    onDeleted={vi.fn()}
                    onSave={onSave}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    expect(screen.queryByRole('menuitem', {name: ru.chatSettings.sections.groups})).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('menuitem', {name: ru.chatSettings.sections.voice}));
    fireEvent.click(screen.getByRole('combobox', {name: ru.chatSettings.voice.source}));
    fireEvent.click(await screen.findByRole('option', {name: ru.chatSettings.voice.inherit}));
    expect(screen.queryByRole('slider', {name: ru.voice.speechRate})).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', {name: ru.save}));
    await waitFor(() =>
        expect(onSave).toHaveBeenCalledWith(
            expect.objectContaining({
                voiceSettingsMode: 'inherit',
                voiceReplyMode: 'always',
                voiceSpeechRate: 1.7,
            }),
        ),
    );
});
