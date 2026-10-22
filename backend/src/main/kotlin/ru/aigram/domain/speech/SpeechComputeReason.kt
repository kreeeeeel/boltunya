package ru.aigram.domain.speech

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SpeechComputeReason {
    @SerialName("ready")
    READY,

    @SerialName("not-installed")
    NOT_INSTALLED,

    @SerialName("cuda-unavailable")
    CUDA_UNAVAILABLE,

    @SerialName("probe-failed")
    PROBE_FAILED,
}
