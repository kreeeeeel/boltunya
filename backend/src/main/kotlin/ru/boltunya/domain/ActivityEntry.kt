package ru.boltunya.domain

import kotlinx.serialization.Serializable

@Serializable
data class ActivityEntry(
    val id: String,
    val chatId: String,
    val title: String,
    val status: String,
    val detail: String,
    val date: Long,
    val durationMs: Long = 0,
    val avatarUrl: String = "",
    val phase: String = "queued",
    val thinking: String = "",
    val queuePosition: Int = 0,
    val priority: Boolean = false,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val attempts: Int = 0,
    val startedAt: Long = 0,
    val finishedAt: Long = 0,
    val waitUntil: Long = 0,
    val preview: String = "",
)
