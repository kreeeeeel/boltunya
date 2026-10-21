package ru.aigram.domain.ai.profile

import kotlinx.serialization.Serializable

@Serializable
data class ModelProviderPreset(
    val id: String,
    val name: String,
    val provider: ModelProvider,
    val baseUrl: String,
    val model: String = "",
    val apiKeyRequired: Boolean = true,
    val defaultsOptions: ModelProfileOptions = ModelProfileOptions(),
    val defaultCapabilities: ModelCapabilities = ModelCapabilities(),
)
