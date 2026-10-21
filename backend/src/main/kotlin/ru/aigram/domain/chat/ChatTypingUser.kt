package ru.aigram.domain.chat

import kotlinx.serialization.Serializable

@Serializable
data class ChatTypingUser(val id: String, val name: String, val action: TypingAction)
