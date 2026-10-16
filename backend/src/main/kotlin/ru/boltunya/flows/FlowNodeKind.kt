package ru.boltunya.flows

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class FlowNodeKind {
    @SerialName("command")
    COMMAND,

    @SerialName("agent")
    AGENT,

    @SerialName("reply")
    REPLY,

    @SerialName("store")
    STORE,

    @SerialName("condition")
    CONDITION,
}
