package ru.aigram.domain.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class VoiceReplyMode {
    @SerialName("text")
    TEXT,

    @SerialName("always")
    ALWAYS,

    @SerialName("random")
    RANDOM,

    @SerialName("mirror")
    MIRROR,
}
