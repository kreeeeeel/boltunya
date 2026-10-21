package ru.aigram.monitoring

import org.junit.jupiter.api.Test
import ru.aigram.domain.system.monitoring.GpuMetrics
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NvidiaMonitorTest {
    private val monitor = NvidiaMonitor()

    @Test
    fun `reads utilization and converts video memory to bytes`() {
        val result = monitor.parse("23, 1024, 8192\n17, 512, 4096")
        assertEquals(GpuMetrics(23.0, 1073741824, 8589934592), result)
    }

    @Test
    fun `does not fabricate metrics for unsupported or invalid readings`() {
        listOf("", "[N/A], 512, 8192", "NaN, 512, 8192", "101, 512, 8192", "20, -1, 8192", "20, 9000, 8192")
            .stream().forEach { assertNull(monitor.parse(it)) }
    }
}
