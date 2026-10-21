package ru.aigram.domain.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ReplyInterruptionMode {
    @SerialName("inherit")
    INHERIT,

    @SerialName("replace")
    REPLACE,

    @SerialName("finish")
    FINISH,

    @SerialName("each")
    EACH,
}
