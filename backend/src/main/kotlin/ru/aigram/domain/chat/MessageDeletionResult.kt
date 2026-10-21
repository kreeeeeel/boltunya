package ru.aigram.domain.chat

import kotlinx.serialization.Serializable

@Serializable
data class MessageDeletionResult(val chatId: String, val messageId: String, val deletedAt: Long)
