package ru.aigram.transformer.system

import ru.aigram.contract.dto.CacheStatistics
import ru.aigram.contract.dto.MetricSample
import ru.aigram.domain.system.CacheStatistics as DomainCacheStatistics
import ru.aigram.domain.system.monitoring.MetricSample as DomainMetricSample

fun DomainCacheStatistics.toContract(): CacheStatistics = CacheStatistics(
    totalBytes,
    fileCount,
    photoBytes,
    videoBytes,
    audioBytes,
    documentBytes,
)

fun DomainMetricSample.toContract(): MetricSample = MetricSample(
    timestamp, cpuPercent.toBigDecimal(), appCpuPercent.toBigDecimal(), memoryUsedBytes,
    memoryTotalBytes, appMemoryBytes, backendMemoryBytes, ollamaMemoryBytes,
    networkReceiveBytesPerSecond, networkSendBytesPerSecond, gpuPercent?.toBigDecimal(),
    gpuMemoryUsedBytes, gpuMemoryTotalBytes,
)
