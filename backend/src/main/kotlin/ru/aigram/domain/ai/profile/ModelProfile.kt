package ru.aigram.domain.ai.profile

import kotlinx.serialization.Serializable

/** Public metadata only. Credentials are held separately and never serialized here. */
@Serializable
data class ModelProfile(
    val id: String,
    val name: String,
    val provider: ModelProvider,
    val preset: String,
    val baseUrl: String,
    val model: String,
    val enabled: Boolean = true,
    val options: ModelProfileOptions = ModelProfileOptions(),
    val capabilities: ModelCapabilities = ModelCapabilities(),
    val apiKeyConfigured: Boolean = false,
)
