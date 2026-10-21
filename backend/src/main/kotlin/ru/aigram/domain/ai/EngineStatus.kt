package ru.aigram.domain.ai

import kotlinx.serialization.Serializable
import ru.aigram.domain.ai.model.ModelInfo

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
    val canRestart: Boolean = false,
)
