package ru.aigram.domain.speech

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SpeechDownloadPhase {
    @SerialName("queued")
    QUEUED,

    @SerialName("downloading")
    DOWNLOADING,

    @SerialName("extracting")
    EXTRACTING,

    @SerialName("ready")
    READY,

    @SerialName("cancelled")
    CANCELLED,

    @SerialName("error")
    ERROR,
}
