package ru.boltunya.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ActivityFeedbackRating {
    @SerialName("good")
    GOOD,

    @SerialName("bad")
    BAD,
}
