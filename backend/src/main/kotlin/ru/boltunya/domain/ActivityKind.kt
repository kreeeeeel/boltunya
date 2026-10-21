package ru.boltunya.domain

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
}
