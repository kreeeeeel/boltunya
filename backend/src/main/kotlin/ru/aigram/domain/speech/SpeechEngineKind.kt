package ru.aigram.domain.speech

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SpeechEngineKind {
    @SerialName("sherpa")
    SHERPA,

    @SerialName("applio-rvc")
    APPLIO_RVC,
}
