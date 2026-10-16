package ru.boltunya.ai.model

data class ModelCompletion(
    val text: String,
    val durationMs: Long,
    val tokensPerSecond: Double,
    val finishReason: String,
)
