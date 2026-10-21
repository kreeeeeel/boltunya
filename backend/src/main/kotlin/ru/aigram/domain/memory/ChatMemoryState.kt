package ru.aigram.domain.memory

import kotlinx.serialization.Serializable

@Serializable
data class ChatMemoryState(
    val chatId: String,
    val accountId: String = "",
    val headId: Long = 0,
    val headDate: Long = 0,
    val backfillBeforeId: Long = 0,
    val backfillComplete: Boolean = false,
    val deltaTargetId: Long = 0,
    val deltaTargetDate: Long = 0,
    val deltaBeforeId: Long = 0,
    val observedId: Long = 0,
    val dirty: Boolean = true,
    val requested: Boolean = false,
    val manual: Boolean = false,
    val ownerSource: Boolean = false,
    val ownerMemoryVersion: Int = 0,
    val factsVersion: Int = 0,
    val processedMessages: Long = 0,
    val updatedAt: Long = 0,
    val recentUpdatedAt: Long = 0,
    val automaticAttemptAt: Long = 0,
    val generation: Long = 0,
    val changeVersion: Long = 0,
)
