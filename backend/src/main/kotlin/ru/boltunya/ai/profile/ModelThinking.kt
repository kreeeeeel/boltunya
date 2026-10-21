package ru.boltunya.ai.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ModelThinking {
    @SerialName("default")
    DEFAULT,

    @SerialName("enabled")
    ENABLED,

    @SerialName("disabled")
    DISABLED,
}
