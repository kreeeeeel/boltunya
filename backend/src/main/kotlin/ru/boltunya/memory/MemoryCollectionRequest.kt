package ru.boltunya.memory

data class MemoryCollectionRequest(val chatId: String, val rebuild: Boolean, val initialProcessedMessages: Long = 0)
