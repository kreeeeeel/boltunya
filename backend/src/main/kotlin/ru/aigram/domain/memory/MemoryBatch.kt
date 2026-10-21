package ru.aigram.domain.memory

import ru.aigram.domain.chat.ChatMessage

data class MemoryBatch(val messages: List<ChatMessage>, val sources: List<MemorySource>)
