package ru.boltunya.ai.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ModelTokenLimit {
    @SerialName("max_tokens")
    MAX_TOKENS,

    @SerialName("max_completion_tokens")
    MAX_COMPLETION_TOKENS,
}
