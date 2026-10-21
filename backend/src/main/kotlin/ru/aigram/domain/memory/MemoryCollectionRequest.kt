package ru.aigram.domain.memory

data class MemoryCollectionRequest(val chatId: String, val rebuild: Boolean, val initialProcessedMessages: Long = 0)
