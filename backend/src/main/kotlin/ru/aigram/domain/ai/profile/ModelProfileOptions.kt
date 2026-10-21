package ru.aigram.domain.ai.profile

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import kotlinx.serialization.Serializable

@Serializable
data class ModelProfileOptions(
    @field:DecimalMin("0.0") @field:DecimalMax("2.0") val temperature: Double? = null,
    @field:DecimalMin("0.0") @field:DecimalMax("1.0") val topP: Double? = null,
    @field:Min(0) @field:Max(1000) val topK: Int? = null,
    @field:DecimalMin("-2.0") @field:DecimalMax("2.0") val presencePenalty: Double? = null,
    @field:DecimalMin("-2.0") @field:DecimalMax("2.0") val frequencyPenalty: Double? = null,
    @field:DecimalMin("0.0") @field:DecimalMax("2.0") val repeatPenalty: Double? = null,
    val seed: Int? = null,
    @field:Size(max = 16) val stop: List<
        @Size(max = 200)
        String,
        > = emptyList(),
    @field:Min(32) @field:Max(131072) val maxOutputTokens: Int = 2048,
    @field:Min(512) @field:Max(2097152) val contextWindow: Int = 8192,
    @field:Min(0) @field:Max(65536) val reasoningTokens: Int = 2048,
    @field:Size(max = 40) val reasoningEffort: String = "default",
    val thinking: ModelThinking = ModelThinking.DEFAULT,
    @field:Min(1) @field:Max(120) val connectTimeoutSeconds: Int = 10,
    @field:Min(10) @field:Max(3600) val idleTimeoutSeconds: Int = 600,
    @field:Min(0) @field:Max(86400) val totalTimeoutSeconds: Int = 0,
    @field:Min(0) @field:Max(1440) val keepAliveMinutes: Int = 20,
)
