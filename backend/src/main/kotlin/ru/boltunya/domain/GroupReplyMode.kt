package ru.boltunya.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class GroupReplyMode {
    @SerialName("inherit")
    INHERIT,

    @SerialName("disabled")
    DISABLED,

    @SerialName("mentions")
    MENTIONS,

    @SerialName("all")
    ALL,
}
