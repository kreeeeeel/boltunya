package ru.aigram.domain

import kotlinx.serialization.Serializable

@Serializable
data class MessageDeletionResult(val chatId: String, val messageId: String, val deletedAt: Long)
