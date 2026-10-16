package ru.boltunya.flows

import kotlinx.serialization.Serializable

@Serializable
data class FlowEdge(val id: String, val source: String, val target: String, val output: String = "")
