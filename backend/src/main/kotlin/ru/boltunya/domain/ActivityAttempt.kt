package ru.boltunya.domain

import kotlinx.serialization.Serializable

@Serializable
data class ActivityAttempt(
    val number: Int,
    val purpose: String,
    val model: String,
    val messages: List<ActivityMessage>,
    val options: String,
    val startedAt: Long,
    val response: String = "",
    val thinking: String = "",
    val durationMs: Long = 0,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val tokensPerSecond: Double = 0.0,
    val loadDurationMs: Long = 0,
    val promptDurationMs: Long = 0,
    val finishReason: String = "",
    val complete: Boolean = false,
)
