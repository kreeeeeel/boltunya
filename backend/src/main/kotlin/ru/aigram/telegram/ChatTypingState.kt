package ru.aigram.telegram

import ru.aigram.domain.ChatTypingUser

internal data class ChatTypingState(val user: ChatTypingUser, val expiresAt: Long)
