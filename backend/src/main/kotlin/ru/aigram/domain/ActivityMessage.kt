package ru.aigram.domain

import kotlinx.serialization.Serializable

@Serializable
data class ActivityMessage(val role: String, val text: String)
