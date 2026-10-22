import {expect, it} from 'vitest';
import {createSettingsDraft} from '@/modules/settings/model/settingsDraft';
import {workspaceState} from '@tests/fixtures/workspace';
it('preserves explicit collection and proactive preferences when saving unrelated settings', () => {
    const draft = createSettingsDraft({
        ...workspaceState.settings,
        memoryCollectionScope: 'all-allowed',
        proactiveEnabled: true,
        proactiveIntervalMinutes: 45,
        formatReplies: true,
        replyProcessingMode: 'finish',
    });
    expect(draft).toMatchObject({
        memoryCollectionScope: 'all-allowed',
        proactiveEnabled: true,
        proactiveIntervalMinutes: 45,
        formatReplies: true,
        replyProcessingMode: 'finish',
    });
});

it('keeps AI formatting opt-in for older saved settings', () => {
    const settings = {...workspaceState.settings};
    delete settings.formatReplies;
    delete settings.replyProcessingMode;
    expect(createSettingsDraft(settings).formatReplies).toBe(false);
    expect(createSettingsDraft(settings).replyProcessingMode).toBe('replace');
});

it('keeps voice recognition opt-in and preserves every explicit speech preference', () => {
    expect(createSettingsDraft(workspaceState.settings)).toMatchObject({
        voiceInputEnabled: false,
        voiceReplyMode: 'text',
        voiceRecognitionModel: 'whisper-tiny',
        voiceSynthesisModel: 'piper-ru-irina',
        voiceSpeechRate: 1,
    });
    const preferences = {
        voiceInputEnabled: true,
        voiceReplyMode: 'mirror' as const,
        voiceReplyProbability: 75,
        voiceRecognitionModel: 'whisper-small',
        voiceSynthesisModel: 'custom-voice',
        voiceSpeechRate: 1.5,
    };
    expect(createSettingsDraft({...workspaceState.settings, ...preferences})).toMatchObject(preferences);
});

it('keeps existing voice conversion on CPU and preserves explicit CUDA without altering other preferences', () => {
    const legacy = {...workspaceState.settings};
    delete legacy.voiceConversionDevice;
    expect(createSettingsDraft(legacy).voiceConversionDevice).toBe('cpu');
    const draft = createSettingsDraft({...legacy, voiceConversionDevice: 'cuda'});
    expect(draft.voiceConversionDevice).toBe('cuda');
    expect(draft.voiceSpeechRate).toBe(1);
    expect(draft.ollamaDevice).toBe(workspaceState.settings.ollamaDevice);
});
