package ru.aigram.domain.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ProactiveMode {
    @SerialName("inherit")
    INHERIT,

    @SerialName("disabled")
    DISABLED,

    @SerialName("enabled")
    ENABLED,
}
