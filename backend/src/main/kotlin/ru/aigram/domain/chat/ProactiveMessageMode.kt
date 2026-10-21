package ru.aigram.domain.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ProactiveMessageMode {
    @SerialName("ai")
    AI,

    @SerialName("text")
    TEXT,
}
