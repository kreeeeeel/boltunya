package ru.boltunya.domain

import kotlinx.serialization.Serializable

@Serializable
data class ActivityStep(val phase: String, val date: Long)
