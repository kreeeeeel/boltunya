package ru.aigram.speech

import kotlinx.serialization.Serializable

@Serializable
data class SpeechWorkerResult(
    val text: String = "",
    val sampleRate: Int = 0,
    val samples: Long = 0,
    val error: String = "",
)
