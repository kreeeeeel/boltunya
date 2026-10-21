package ru.aigram.domain

import kotlinx.serialization.Serializable

@Serializable
data class ChatTypingUser(val id: String, val name: String, val action: TypingAction)
