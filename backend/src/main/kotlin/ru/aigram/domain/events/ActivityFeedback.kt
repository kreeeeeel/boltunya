package ru.aigram.domain.events

import kotlinx.serialization.Serializable

@Serializable
data class ActivityFeedback(val rating: ActivityFeedbackRating, val comment: String, val updatedAt: Long)
