package ru.aigram.domain.telegram

data class TelegramAutomatedSend(
    val sendingId: Int,
    val ownerId: String,
    val version: Long,
    val chatId: Long,
    val createdAt: Long,
)
