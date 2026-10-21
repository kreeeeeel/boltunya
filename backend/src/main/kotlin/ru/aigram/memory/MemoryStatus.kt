package ru.aigram.memory

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MemoryStatus {
    @SerialName("disabled")
    DISABLED,

    @SerialName("idle")
    IDLE,

    @SerialName("queued")
    QUEUED,

    @SerialName("processing")
    PROCESSING,

    @SerialName("ready")
    READY,

    @SerialName("waiting_model")
    WAITING_MODEL,

    @SerialName("error")
    ERROR,
}
