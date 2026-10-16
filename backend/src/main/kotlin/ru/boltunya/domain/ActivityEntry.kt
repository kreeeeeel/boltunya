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
)
