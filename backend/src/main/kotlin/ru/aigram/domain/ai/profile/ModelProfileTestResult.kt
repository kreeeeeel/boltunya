package ru.aigram.domain.ai.profile

import kotlinx.serialization.Serializable

@Serializable
data class ModelProfileTestResult(
    val ok: Boolean,
    val code: String = "",
    val message: String = "",
    val durationMs: Long = 0,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val finishReason: String = "",
    val preview: String = "",
)
