package ru.aigram.speech

import kotlinx.serialization.Serializable
import ru.aigram.domain.speech.SpeechAsset

@Serializable
data class SpeechAssetDefinition(
    val asset: SpeechAsset,
    val downloadUrl: String,
    val sha256: String,
    val archiveRoot: String = "",
    val requiredFiles: List<String>,
    val modelFile: String = "",
)
