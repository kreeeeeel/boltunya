package ru.boltunya.ai.research

import kotlinx.serialization.Serializable

@Serializable
data class ResearchContext(
    val status: String = "none",
    val facts: String = "",
    val sources: List<ResearchSource> = emptyList(),
)
