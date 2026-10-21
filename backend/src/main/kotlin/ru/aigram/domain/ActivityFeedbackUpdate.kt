package ru.aigram.domain

import jakarta.validation.constraints.Size
import kotlinx.serialization.Serializable

@Serializable
data class ActivityFeedbackUpdate(
    val rating: ActivityFeedbackRating,
    @field:Size(max = 1000) val comment: String? = null,
)
