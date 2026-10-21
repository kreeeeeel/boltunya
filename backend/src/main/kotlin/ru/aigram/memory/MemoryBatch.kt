package ru.aigram.memory

import ru.aigram.domain.ChatMessage

data class MemoryBatch(val messages: List<ChatMessage>, val sources: List<MemorySource>)
