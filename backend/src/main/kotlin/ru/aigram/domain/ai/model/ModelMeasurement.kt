package ru.aigram.domain.ai.model

import kotlinx.serialization.Serializable

@Serializable
data class ModelMeasurement(val durationMs: Long, val tokensPerSecond: Double)
