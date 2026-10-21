package ru.aigram.ai.prompt

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import kotlinx.serialization.Serializable

@Serializable
data class ModelPromptSettings(
    @field:NotBlank @field:Size(max = 160) val model: String,
    @field:Size(max = 36) val profileId: String = "",
    @field:Size(max = 20000) val instructions: String = "",
    @field:Min(512) @field:Max(2097152) val contextWindow: Int? = null,
    val tokenEncoding: TokenEncoding = TokenEncoding.AUTO,
)
