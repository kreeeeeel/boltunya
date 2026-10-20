package ru.boltunya.ai

import org.junit.jupiter.api.Test
import ru.boltunya.resources.ResourceCatalog
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NvidiaDeviceDiscoveryTest {
    private val discovery = NvidiaDeviceDiscovery(ResourceCatalog(), Clock.systemUTC())

    @Test
    fun `uses Nvidia UUIDs and reported VRAM instead of assuming hardware enumeration indices`() {
        val first = "GPU-11111111-1111-1111-1111-111111111111"
        val second = "GPU-22222222-2222-2222-2222-222222222222"
        val devices = discovery.parse(
            "$first, 12288, 8.6, 580.88, NVIDIA RTX 3060\n" +
                "$second, 24576, 8.9, 580.88, NVIDIA card, custom name\n" +
                "0, 8192, 8.6, 580.88, OSHI or CUDA numeric index\n" +
                "PCI\\VEN_10DE, 8192, 8.6, 580.88, Hardware identifier",
        )
        assertEquals(listOf(first, second), devices.map { it.id })
        assertEquals(12288L * 1024 * 1024, devices.first().memoryBytes)
        assertEquals("NVIDIA card, custom name", devices.last().name)
        assertTrue(devices.all { it.selectable && it.kind == "nvidia" })
    }

    @Test
    fun `rejects unsupported compute and warns about older drivers without falsely blocking them`() {
        val devices = discovery.parse(
            "GPU-11111111-1111-1111-1111-111111111111, 8192, 4.0, 580.88, Old compute\n" +
                "GPU-22222222-2222-2222-2222-222222222222, 8192, 6.1, 560.88, Old driver\n" +
                "GPU-33333333-3333-3333-3333-333333333333, 8192, 6.1, 570.88, Compatible legacy\n" +
                "GPU-44444444-4444-4444-4444-444444444444, -1, NaN, 580.88, Invalid data",
        )
        assertEquals(3, devices.size)
        assertEquals("compute", devices[0].reason)
        assertFalse(devices[0].selectable)
        assertEquals("driver", devices[1].reason)
        assertTrue(devices[1].selectable)
        assertTrue(devices[2].selectable)
        assertTrue(discovery.parse("N/A\nmalformed").isEmpty())
    }
}
