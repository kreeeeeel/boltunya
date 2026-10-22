package ru.aigram.domain.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class VoiceSettingsMode {
    @SerialName("inherit")
    INHERIT,

    @SerialName("override")
    OVERRIDE,
}
