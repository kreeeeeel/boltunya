package ru.boltunya.domain

import kotlinx.serialization.Serializable

@Serializable
data class AppearanceSettings(
    val mode: String = "system",
    val tokens: Map<String, String> = emptyMap(),
    val css: String = "",
)
