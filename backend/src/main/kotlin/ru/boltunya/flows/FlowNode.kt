package ru.boltunya.flows

import kotlinx.serialization.Serializable

@Serializable
data class FlowNode(
    val id: String,
    val kind: FlowNodeKind,
    val text: String = "",
    val key: String = "",
    val operation: String = "set",
    val match: String = "prefix",
    val x: Double = 0.0,
    val y: Double = 0.0,
)
