package ru.aigram.ai.model

data class ModelCompletion(
    val text: String,
    val durationMs: Long,
    val tokensPerSecond: Double,
    val finishReason: String,
    val rawText: String = text,
    val thinking: String = "",
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val loadDurationMs: Long = 0,
    val promptDurationMs: Long = 0,
)
