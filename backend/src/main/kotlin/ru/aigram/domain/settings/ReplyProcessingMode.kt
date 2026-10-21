package ru.aigram.domain.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ReplyProcessingMode {
    @SerialName("replace")
    REPLACE,

    @SerialName("finish")
    FINISH,

    @SerialName("each")
    EACH,
}
