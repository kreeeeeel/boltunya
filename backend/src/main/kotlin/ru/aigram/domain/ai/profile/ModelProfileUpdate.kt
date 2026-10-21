package ru.aigram.domain.ai.profile

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import kotlinx.serialization.Serializable

@Serializable
data class ModelProfileUpdate(
    @field:NotBlank @field:Size(max = 80) val name: String,
    val provider: ModelProvider,
    @field:Size(max = 30) val preset: String = "custom",
    @field:NotBlank @field:Size(max = 500) val baseUrl: String,
    @field:NotBlank @field:Size(max = 160) val model: String,
    val enabled: Boolean = true,
    @field:Valid val options: ModelProfileOptions = ModelProfileOptions(),
    @field:Valid val capabilities: ModelCapabilities = ModelCapabilities(),
    @field:Size(max = 8192) val apiKey: String? = null,
    val clearApiKey: Boolean = false,
)
