package ru.aigram.monitoring

data class GpuMetrics(val percent: Double, val usedBytes: Long, val totalBytes: Long)
