package ru.boltunya.ai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ThinkingMode {
    @SerialName("default")
    DEFAULT,

    @SerialName("disabled")
    DISABLED,

    @SerialName("low")
    LOW,
}
