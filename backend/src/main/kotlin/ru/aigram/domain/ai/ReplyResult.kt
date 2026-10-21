package ru.aigram.domain.ai

import kotlinx.serialization.Serializable

@Serializable
data class ReplyResult(
    val text: String,
    val durationMs: Long,
    val tokensPerSecond: Double,
    val model: String = "",
    val stickerId: String = "",
)
