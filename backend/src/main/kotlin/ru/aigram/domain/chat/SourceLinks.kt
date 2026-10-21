package ru.aigram.domain.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SourceLinks {
    @SerialName("requested")
    REQUESTED,

    @SerialName("always")
    ALWAYS,

    @SerialName("never")
    NEVER,
}
