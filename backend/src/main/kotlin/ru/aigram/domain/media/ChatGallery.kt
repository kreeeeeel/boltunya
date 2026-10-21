package ru.aigram.domain.media

import kotlinx.serialization.Serializable
import ru.aigram.domain.chat.ChatMessage

@Serializable
data class ChatGallery(
    val messages: List<ChatMessage>,
    val hasMoreBefore: Boolean = false,
    val hasMoreAfter: Boolean = false,
)
