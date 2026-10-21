package ru.aigram.ai.profile

import kotlinx.serialization.Serializable

@Serializable
data class ModelCapabilities(
    val streaming: Boolean = true,
    val vision: Boolean = false,
    val structuredOutput: Boolean = false,
    val reasoning: Boolean = false,
    val temperature: Boolean = true,
    val topP: Boolean = true,
    val topK: Boolean = false,
    val penalties: Boolean = false,
    val tokenLimit: ModelTokenLimit = ModelTokenLimit.MAX_TOKENS,
)
