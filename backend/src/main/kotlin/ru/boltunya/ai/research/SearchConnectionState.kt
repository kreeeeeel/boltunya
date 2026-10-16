package ru.boltunya.ai.research

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SearchConnectionState {
    @SerialName("unchecked")
    UNCHECKED,

    @SerialName("ready")
    READY,

    @SerialName("limited")
    LIMITED,

    @SerialName("unavailable")
    UNAVAILABLE,
}
