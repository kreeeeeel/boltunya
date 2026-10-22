package ru.aigram.domain.speech

import ru.aigram.domain.settings.VoiceReplyMode

data class VoicePreferences(
    val inputEnabled: Boolean,
    val replyMode: VoiceReplyMode,
    val replyProbability: Int,
    val recognitionModel: String,
    val synthesisModel: String,
    val speechRate: Double,
    val conversionDevice: SpeechComputeDevice = SpeechComputeDevice.CPU,
)
