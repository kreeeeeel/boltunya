package ru.aigram.domain.ai.prompt

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TokenEncoding {
    @SerialName("auto")
    AUTO,

    @SerialName("cl100k")
    CL100K,

    @SerialName("o200k")
    O200K,
}
