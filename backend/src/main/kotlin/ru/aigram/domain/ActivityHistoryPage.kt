package ru.aigram.domain

import kotlinx.serialization.Serializable

@Serializable
data class ActivityHistoryPage(
    val entries: List<ActivityEntry> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val total: Int = 0,
    val storage: ActivityHistoryStorage = ActivityHistoryStorage(),
)
