package ru.boltunya.memory

import kotlinx.serialization.Serializable

@Serializable
data class MemoryCollectionProgress(
    val active: Boolean = false,
    val status: MemoryStatus = MemoryStatus.IDLE,
    val queuedChats: Int = 0,
    val queuePosition: Int = 0,
    val processedMessages: Long = 0,
)
