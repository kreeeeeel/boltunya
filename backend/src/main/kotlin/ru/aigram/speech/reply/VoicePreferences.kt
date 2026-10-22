package ru.aigram.speech.reply

import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.chat.VoiceSettingsMode
import ru.aigram.domain.settings.AppSettings
import ru.aigram.domain.speech.VoicePreferences

fun AppSettings.voicePreferences(rule: ChatRule): VoicePreferences = if (
    rule.voiceSettingsMode == VoiceSettingsMode.OVERRIDE
) {
    VoicePreferences(
        rule.voiceInputEnabled,
        rule.voiceReplyMode,
        rule.voiceReplyProbability,
        rule.voiceRecognitionModel,
        rule.voiceSynthesisModel,
        rule.voiceSpeechRate,
    )
} else {
    VoicePreferences(
        voiceInputEnabled,
        voiceReplyMode,
        voiceReplyProbability,
        voiceRecognitionModel,
        voiceSynthesisModel,
        voiceSpeechRate,
    )
}
