package ru.aigram.domain.ai.model

import kotlinx.serialization.Serializable

@Serializable
data class ModelPolicy(
    val thinking: ThinkingMode = ThinkingMode.DEFAULT,
    val reasoningTokens: Int = 0,
    val minimumTemperature: Double = 0.0,
    val maximumTemperature: Double = 1.0,
)
