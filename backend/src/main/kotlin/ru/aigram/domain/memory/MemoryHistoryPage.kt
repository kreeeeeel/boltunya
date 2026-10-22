package ru.aigram.domain.memory

import ru.aigram.domain.chat.ChatMessage

data class MemoryHistoryPage(val messages: List<ChatMessage>, val exhausted: Boolean)
