package ru.aigram.transformer.system

import org.springframework.stereotype.Component
import ru.aigram.contract.dto.CacheStatistics
import ru.aigram.contract.dto.MetricSample
import ru.aigram.domain.system.CacheStatistics as DomainCacheStatistics
import ru.aigram.domain.system.monitoring.MetricSample as DomainMetricSample

@Component
class ResourcesTransformer {
    fun toContract(value: DomainCacheStatistics): CacheStatistics = CacheStatistics(
        value.totalBytes,
        value.fileCount,
        value.photoBytes,
        value.videoBytes,
        value.audioBytes,
        value.documentBytes,
    )

    fun toContract(value: DomainMetricSample): MetricSample = MetricSample(
        value.timestamp, value.cpuPercent.toBigDecimal(), value.appCpuPercent.toBigDecimal(), value.memoryUsedBytes,
        value.memoryTotalBytes, value.appMemoryBytes, value.backendMemoryBytes, value.ollamaMemoryBytes,
        value.networkReceiveBytesPerSecond, value.networkSendBytesPerSecond, value.gpuPercent?.toBigDecimal(),
        value.gpuMemoryUsedBytes, value.gpuMemoryTotalBytes,
    )
}
