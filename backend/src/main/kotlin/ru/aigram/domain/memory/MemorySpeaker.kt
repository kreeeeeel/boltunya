package ru.aigram.domain.memory

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MemorySpeaker {
    @SerialName("owner")
    OWNER,

    @SerialName("assistant")
    ASSISTANT,

    @SerialName("contact")
    CONTACT,
}
