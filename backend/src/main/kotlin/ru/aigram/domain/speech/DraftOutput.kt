package ru.aigram.domain.speech

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class DraftOutput {
    @SerialName("text")
    TEXT,

    @SerialName("voice")
    VOICE,
}
