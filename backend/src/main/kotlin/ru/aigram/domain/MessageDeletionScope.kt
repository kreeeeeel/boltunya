package ru.aigram.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MessageDeletionScope {
    @SerialName("self")
    SELF,

    @SerialName("everyone")
    EVERYONE,
}
