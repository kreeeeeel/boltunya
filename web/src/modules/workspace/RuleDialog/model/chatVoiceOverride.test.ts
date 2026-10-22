import {expect, it} from 'vitest';
import {chatVoiceOverride} from '@/modules/workspace/RuleDialog/model/chatVoiceOverride';
import {workspaceState} from '@tests/fixtures/workspace';

it('copies the effective shared speech preferences without enabling the chat or mutating settings', () => {
    const rule = {...workspaceState.chats[0].rule, enabled: false};
    const settings = {
        ...workspaceState.settings,
        voiceInputEnabled: true,
        voiceReplyMode: 'random' as const,
        voiceReplyProbability: 73,
        voiceRecognitionModel: 'whisper-tiny',
        voiceSynthesisModel: 'piper-ru-irina',
        voiceSpeechRate: 1.4,
    };
    const before = structuredClone(settings);
    expect(chatVoiceOverride(rule, settings)).toMatchObject({
        enabled: false,
        voiceSettingsMode: 'override',
        voiceInputEnabled: true,
        voiceReplyMode: 'random',
        voiceReplyProbability: 73,
        voiceRecognitionModel: settings.voiceRecognitionModel,
        voiceSynthesisModel: settings.voiceSynthesisModel,
        voiceSpeechRate: 1.4,
    });
    expect(settings).toEqual(before);
    expect(rule).not.toHaveProperty('voiceSettingsMode', 'override');
});

it('uses speech defaults for a legacy caller without shared settings', () => {
    expect(chatVoiceOverride(workspaceState.chats[0].rule)).toMatchObject({
        voiceSettingsMode: 'override',
        voiceInputEnabled: false,
        voiceReplyMode: 'text',
        voiceReplyProbability: 50,
        voiceRecognitionModel: 'whisper-tiny',
        voiceSynthesisModel: 'piper-ru-irina',
        voiceSpeechRate: 1,
    });
});
