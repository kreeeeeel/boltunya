package ru.boltunya.domain

import kotlinx.serialization.Serializable

@Serializable
data class ActivityFeedback(val rating: ActivityFeedbackRating, val comment: String, val updatedAt: Long)
