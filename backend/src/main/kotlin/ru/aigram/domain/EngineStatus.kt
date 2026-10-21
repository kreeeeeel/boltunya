package ru.aigram.domain

import kotlinx.serialization.Serializable

@Serializable
data class EngineStatus(
    val online: Boolean,
    val models: List<ModelInfo>,
    val downloading: String = "",
    val progress: Int = 0,
    val error: String = "",
    val warming: String = "",
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val downloadStage: String = "download",
    val installed: Boolean = false,
)
