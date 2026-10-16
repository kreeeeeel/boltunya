package ru.boltunya.memory

import ru.boltunya.domain.ChatMessage

data class MemoryBatch(val messages: List<ChatMessage>, val sources: List<MemorySource>)
