package ru.aigram.domain.speech

import kotlinx.serialization.Serializable

@Serializable
data class SpeechDownload(
    val assetId: String,
    val phase: SpeechDownloadPhase,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val error: String = "",
)
