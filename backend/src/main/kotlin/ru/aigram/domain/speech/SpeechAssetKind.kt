package ru.aigram.domain.speech

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SpeechAssetKind {
    @SerialName("runtime")
    RUNTIME,

    @SerialName("recognition")
    RECOGNITION,

    @SerialName("synthesis")
    SYNTHESIS,
}
