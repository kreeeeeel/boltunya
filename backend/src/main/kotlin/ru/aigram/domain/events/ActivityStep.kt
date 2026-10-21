package ru.aigram.domain.events

import kotlinx.serialization.Serializable

@Serializable
data class ActivityStep(val phase: String, val date: Long)
