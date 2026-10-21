package ru.boltunya.telegram

import ru.boltunya.domain.ChatTypingUser

internal data class ChatTypingState(val user: ChatTypingUser, val expiresAt: Long)
