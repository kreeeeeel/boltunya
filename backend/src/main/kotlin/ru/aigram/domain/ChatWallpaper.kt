package ru.aigram.domain

import kotlinx.serialization.Serializable

@Serializable
data class ChatWallpaper(
    val kind: String = "fill",
    val url: String = "",
    val colors: List<String> = emptyList(),
    val rotation: Int = 0,
    val blurred: Boolean = false,
    val dimming: Int = 0,
    val intensity: Int = 50,
)
