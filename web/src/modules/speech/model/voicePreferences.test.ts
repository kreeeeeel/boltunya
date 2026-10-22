import {expect, it} from 'vitest';
import {workspaceState} from '@tests/fixtures/workspace';
import {voicePreferences} from '@/modules/speech/model/voicePreferences';

it('inherits global voice settings until this chat explicitly opts into its own configuration', () => {
    const settings = workspaceState.settings;
    const chat = workspaceState.chats[0];
    expect(
        voicePreferences(settings, {
            ...chat.rule,
            voiceSettingsMode: 'inherit',
            voiceSynthesisModel: 'unused-draft',
        }),
    ).toMatchObject({
        voiceSynthesisModel: settings.voiceSynthesisModel ?? 'piper-ru-irina',
        voiceSpeechRate: settings.voiceSpeechRate ?? 1,
    });
    const rule = {
        ...chat.rule,
        voiceSettingsMode: 'override' as const,
        voiceSynthesisModel: 'personal-voice',
    };
    expect(voicePreferences(settings, rule)).toMatchObject({
        voiceSynthesisModel: 'personal-voice',
        voiceSpeechRate: 1,
        voiceReplyMode: 'text',
    });
    expect(voicePreferences(settings)).toMatchObject({
        voiceSynthesisModel: 'piper-ru-irina',
        voiceSpeechRate: 1,
    });
});
