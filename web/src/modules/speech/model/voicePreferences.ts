import type {AppState, ChatRule} from '@/shared/api/client';

export function voicePreferences(settings: AppState['settings'], rule?: ChatRule) {
    const value = rule?.voiceSettingsMode === 'override' ? rule : settings;
    return {
        voiceInputEnabled: value.voiceInputEnabled ?? false,
        voiceReplyMode: value.voiceReplyMode ?? 'text',
        voiceReplyProbability: value.voiceReplyProbability ?? 50,
        voiceRecognitionModel: value.voiceRecognitionModel ?? 'whisper-tiny',
        voiceSynthesisModel: value.voiceSynthesisModel ?? 'piper-ru-irina',
        voiceSpeechRate: value.voiceSpeechRate ?? 1,
    };
}
