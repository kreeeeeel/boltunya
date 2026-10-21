package ru.aigram.domain.system.monitoring

import kotlinx.serialization.Serializable

@Serializable
data class MetricSample(
    val timestamp: Long,
    val cpuPercent: Double,
    val appCpuPercent: Double,
    val memoryUsedBytes: Long,
    val memoryTotalBytes: Long,
    val appMemoryBytes: Long,
    val backendMemoryBytes: Long,
    val ollamaMemoryBytes: Long,
    val networkReceiveBytesPerSecond: Long,
    val networkSendBytesPerSecond: Long,
    val gpuPercent: Double? = null,
    val gpuMemoryUsedBytes: Long? = null,
    val gpuMemoryTotalBytes: Long? = null,
)
