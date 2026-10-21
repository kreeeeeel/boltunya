package ru.aigram.domain.telegram

import kotlinx.serialization.Serializable

@Serializable
data class TelegramStatus(
    val state: String = "disconnected",
    val name: String = "",
    val prompt: String = "",
    val error: String = "",
    val avatarUrl: String = "",
    val userId: String = "",
)
