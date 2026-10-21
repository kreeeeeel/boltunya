package ru.aigram.domain.events

import kotlinx.serialization.Serializable

@Serializable
data class ActivityDetails(
    val entry: ActivityEntry,
    val trigger: String,
    val response: String = "",
    val attempts: List<ActivityAttempt> = emptyList(),
    val steps: List<ActivityStep> = emptyList(),
    val feedback: ActivityFeedback? = null,
)
