package ru.aigram.domain

import kotlinx.serialization.Serializable

@Serializable
data class ActivityHistoryStorage(
    val retentionDays: Int = 0,
    val maxStorageMb: Int = 0,
    val bytes: Long = 0,
    val recording: Boolean = true,
    val errorCode: String = "",
)
