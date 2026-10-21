package ru.aigram.automation.proactive

import kotlinx.serialization.Serializable

@Serializable
data class ProactiveRecord(
    val sourceMessageId: String,
    val attemptedAt: Long,
    val lastIncomingId: String = "",
    val awaitingResponse: Boolean = false,
)
