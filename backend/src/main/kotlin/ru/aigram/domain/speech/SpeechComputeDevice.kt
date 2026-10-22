package ru.aigram.domain.speech

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SpeechComputeDevice {
    @SerialName("cpu")
    CPU,

    @SerialName("cuda")
    CUDA,
}
