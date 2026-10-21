package ru.aigram.domain.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class DeliveryMode {
    @SerialName("inherit")
    INHERIT,

    @SerialName("reply")
    REPLY,

    @SerialName("send")
    SEND,
}
