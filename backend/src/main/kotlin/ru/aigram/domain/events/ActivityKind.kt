package ru.aigram.domain.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ActivityKind {
    @SerialName("reply")
    REPLY,

    @SerialName("draft")
    DRAFT,

    @SerialName("memory")
    MEMORY,

    @SerialName("initiative")
    INITIATIVE,
}
