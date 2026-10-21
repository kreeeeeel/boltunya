package ru.aigram.domain.speech

import kotlinx.serialization.Serializable

@Serializable
data class SpeechAsset(
    val id: String,
    val title: String,
    val kind: SpeechAssetKind,
    val languages: List<String>,
    val sizeBytes: Long,
    val license: String,
    val sourceUrl: String,
    val installed: Boolean = false,
    val dependencies: List<String> = emptyList(),
    val userProvided: Boolean = false,
)
