import type {AppState, ChatRule} from '@/shared/api/client';

export function chatVoiceOverride(rule: ChatRule, settings?: AppState['settings']): ChatRule {
    return {
        ...rule,
        voiceSettingsMode: 'override',
        voiceInputEnabled: settings?.voiceInputEnabled ?? false,
        voiceReplyMode: settings?.voiceReplyMode ?? 'text',
        voiceReplyProbability: settings?.voiceReplyProbability ?? 50,
        voiceRecognitionModel: settings?.voiceRecognitionModel ?? 'whisper-tiny',
        voiceSynthesisModel: settings?.voiceSynthesisModel ?? 'piper-ru-irina',
        voiceSpeechRate: settings?.voiceSpeechRate ?? 1,
    };
}
