package ru.aigram.domain

import kotlinx.serialization.Serializable

@Serializable
data class ChatGallery(
    val messages: List<ChatMessage>,
    val hasMoreBefore: Boolean = false,
    val hasMoreAfter: Boolean = false,
)
