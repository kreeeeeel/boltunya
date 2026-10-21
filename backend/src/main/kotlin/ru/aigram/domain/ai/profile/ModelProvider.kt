package ru.aigram.domain.ai.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ModelProvider {
    @SerialName("ollama")
    OLLAMA,

    @SerialName("openai-compatible")
    OPENAI_COMPATIBLE,
}
