package ru.boltunya.flows

import kotlinx.serialization.Serializable

@Serializable
data class AutomationFlow(
    val id: String,
    val name: String,
    val enabled: Boolean = false,
    val chatIds: List<String> = emptyList(),
    val nodes: List<FlowNode> = emptyList(),
    val edges: List<FlowEdge> = emptyList(),
)
