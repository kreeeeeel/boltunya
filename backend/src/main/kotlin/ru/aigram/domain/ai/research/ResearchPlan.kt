package ru.aigram.domain.ai.research

import kotlinx.serialization.Serializable

@Serializable
data class ResearchPlan(
    val kind: String = "none",
    val query: String = "",
    val location: String = "",
    val dayOffset: Int = 0,
    val weatherPeriod: String = "current",
)
