package ru.aigram.transformer.system

import org.junit.jupiter.api.Test
import ru.aigram.domain.system.CacheStatistics
import ru.aigram.domain.system.monitoring.MetricSample
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResourcesTransformerTest {
    private val transformer = ResourcesTransformer()

    @Test
    fun `monitoring preserves memory above two gibibytes and optional gpu metrics`() {
        val value = MetricSample(
            timestamp = 1_790_000_000_000L, cpuPercent = 37.5, appCpuPercent = 2.25,
            memoryUsedBytes = 20_000_000_000L, memoryTotalBytes = 32_000_000_000L,
            appMemoryBytes = 5_000_000_000L, backendMemoryBytes = 3_000_000_000L,
            ollamaMemoryBytes = 8_000_000_000L, networkReceiveBytesPerSecond = 7_000_000_000L,
            networkSendBytesPerSecond = 9_000_000_000L, gpuPercent = 72.5,
            gpuMemoryUsedBytes = 6_000_000_000L, gpuMemoryTotalBytes = 8_000_000_000L,
        )
        val mapped = transformer.toContract(value)

        assertEquals(value.timestamp, mapped.timestamp)
        assertEquals(value.memoryTotalBytes, mapped.memoryTotalBytes)
        assertEquals(value.appMemoryBytes, mapped.appMemoryBytes)
        assertEquals(value.networkReceiveBytesPerSecond, mapped.networkReceiveBytesPerSecond)
        assertEquals(value.gpuMemoryUsedBytes, mapped.gpuMemoryUsedBytes)
        assertEquals(value.gpuMemoryTotalBytes, mapped.gpuMemoryTotalBytes)
        assertEquals(BigDecimal("37.5"), mapped.cpuPercent)
        assertEquals(BigDecimal("72.5"), mapped.gpuPercent)
        val unavailable = transformer.toContract(
            value.copy(gpuPercent = null, gpuMemoryUsedBytes = null, gpuMemoryTotalBytes = null),
        )
        assertNull(unavailable.gpuPercent)
        assertNull(unavailable.gpuMemoryUsedBytes)
    }

    @Test
    fun `cache response retains byte categories without truncation`() {
        val value = CacheStatistics(9_000_000_000L, 100, 4_000_000_000L, 3_000_000_000L, 1_000_000_000L, 1_000_000_000L)
        val mapped = transformer.toContract(value)

        assertEquals(value.totalBytes, mapped.totalBytes)
        assertEquals(value.photoBytes, mapped.photoBytes)
        assertEquals(value.videoBytes, mapped.videoBytes)
        assertEquals(value.documentBytes, mapped.documentBytes)
    }
}
