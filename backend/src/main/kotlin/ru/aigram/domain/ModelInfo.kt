package ru.aigram.domain

import kotlinx.serialization.Serializable

@Serializable
data class ModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val size: String,
    val installed: Boolean,
    val recommended: Boolean = false,
    val supportsAutomation: Boolean = true,
    val family: String = "qwen",
    val speed: String = "balanced",
    val quality: String = "good",
    val thinking: Boolean = false,
    val memoryGb: Int = 8,
    val contextTokens: Int = 8192,
    val license: String = "Apache-2.0",
    val measurement: ModelMeasurement? = null,
)
