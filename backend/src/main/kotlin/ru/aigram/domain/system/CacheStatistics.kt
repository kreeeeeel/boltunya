package ru.aigram.domain.system

import kotlinx.serialization.Serializable

@Serializable
data class CacheStatistics(
    val totalBytes: Long = 0,
    val fileCount: Long = 0,
    val photoBytes: Long = 0,
    val videoBytes: Long = 0,
    val audioBytes: Long = 0,
    val documentBytes: Long = 0,
)
