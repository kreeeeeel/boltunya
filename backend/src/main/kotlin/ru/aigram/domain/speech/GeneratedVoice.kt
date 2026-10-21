package ru.aigram.domain.speech

import kotlinx.serialization.Serializable

@Serializable
data class GeneratedVoice(
    val id: String,
    val url: String,
    val mimeType: String,
    val duration: Int,
    val waveform: List<Int>,
)
