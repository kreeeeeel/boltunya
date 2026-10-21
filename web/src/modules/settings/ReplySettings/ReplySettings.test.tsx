import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen} from '@testing-library/react';
import {expect, it, vi} from 'vitest';

import {ReplySettings} from '@/modules/settings/ReplySettings/ReplySettings';
import {createSettingsDraft} from '@/modules/settings/model/settingsDraft';
import {workspaceState} from '@tests/fixtures/workspace';
import ru from '@/shared/i18n/ru.json';

it('keeps reply controls separate while preserving proactive preferences in the shared draft', () => {
    const onUpdate = vi.fn();
    const value = {
        ...createSettingsDraft(workspaceState.settings),
        proactiveEnabled: true,
        proactiveIntervalMinutes: 45,
    };
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ReplySettings value={value} onUpdate={onUpdate} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    expect(screen.queryByRole('switch', {name: ru.proactive.title})).toBeNull();
    fireEvent.click(screen.getByRole('switch', {name: ru.showTyping}));
    expect(onUpdate).toHaveBeenCalledWith(
        expect.objectContaining({
            proactiveEnabled: true,
            proactiveIntervalMinutes: 45,
            showTyping: false,
        }),
    );
});

it('configures automatic voice replies in Replies while preserving selected speech models and STT', async () => {
    const update = vi.fn();
    const value = {
        ...createSettingsDraft(workspaceState.settings),
        voiceInputEnabled: true,
        voiceSynthesisModel: 'custom-voice',
        voiceReplyMode: 'random' as const,
        voiceReplyProbability: 25,
    };
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ReplySettings value={value} onUpdate={update} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    expect(screen.getByRole('combobox', {name: ru.voice.replyMode})).toHaveTextContent(
        ru.voice.replyModes.random,
    );
    // rc-slider reads the legacy key fields that browsers also supply for End.
    fireEvent.keyDown(screen.getByRole('slider', {name: ru.voice.probability}), {
        key: 'End',
        code: 'End',
        keyCode: 35,
        which: 35,
    });
    expect(update).toHaveBeenCalledWith(
        expect.objectContaining({
            voiceReplyProbability: 100,
            voiceSynthesisModel: 'custom-voice',
            voiceInputEnabled: true,
        }),
    );
    fireEvent.click(screen.getByRole('combobox', {name: ru.voice.replyMode}));
    fireEvent.click(await screen.findByRole('option', {name: ru.voice.replyModes.mirror}));
    expect(update).toHaveBeenCalledWith(
        expect.objectContaining({
            voiceReplyMode: 'mirror',
            voiceSynthesisModel: 'custom-voice',
            voiceInputEnabled: true,
        }),
    );
});
