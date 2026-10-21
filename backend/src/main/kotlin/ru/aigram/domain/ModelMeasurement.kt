package ru.aigram.domain

import kotlinx.serialization.Serializable

@Serializable
data class ModelMeasurement(val durationMs: Long, val tokensPerSecond: Double)
