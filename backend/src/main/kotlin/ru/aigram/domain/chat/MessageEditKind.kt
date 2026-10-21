package ru.aigram.domain.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MessageEditKind {
    @SerialName("text")
    TEXT,

    @SerialName("caption")
    CAPTION,
}
