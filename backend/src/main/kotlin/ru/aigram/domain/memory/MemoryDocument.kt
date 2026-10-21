package ru.aigram.domain.memory

import kotlinx.serialization.Serializable

@Serializable
data class MemoryDocument(
    val chatId: String,
    val notes: String,
    val summary: String,
    val revision: String,
    val updatedAt: Long = 0,
    val processedMessages: Long = 0,
    val backfillComplete: Boolean = false,
    val status: MemoryStatus = MemoryStatus.IDLE,
    val error: String = "",
    val trackedChats: Int = 0,
    val relativePath: String = "",
    val collection: MemoryCollectionProgress = MemoryCollectionProgress(),
)
