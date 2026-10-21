package ru.aigram.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ContextExclusionScope {
    @SerialName("message")
    MESSAGE,

    @SerialName("through")
    THROUGH,

    @SerialName("from")
    FROM,
}
