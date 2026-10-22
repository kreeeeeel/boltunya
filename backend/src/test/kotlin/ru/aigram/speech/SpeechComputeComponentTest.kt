package ru.aigram.speech

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.component.speech.SpeechComputeComponent
import ru.aigram.domain.speech.SpeechComputeDevice
import ru.aigram.domain.speech.SpeechComputeReason
import ru.aigram.exception.impl.SpeechInferenceFailedException
import ru.aigram.storage.SettingsStore
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpeechComputeComponentTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `missing optional runtime never invokes torch downloads or background probes`() {
        val calls = AtomicInteger()
        val download = SpeechDownloadClient { _, _, _ -> throw SpeechInferenceFailedException() }
        speechCatalog(directory, download).use { catalog ->
            val component = SpeechComputeComponent(SettingsStore(directory), catalog) {
                calls.incrementAndGet()
                throw SpeechInferenceFailedException()
            }
            assertEquals(0, calls.get())
            val status = component.status()
            assertEquals(SpeechComputeDevice.CPU, status.selectedDevice)
            assertEquals(SpeechComputeReason.NOT_INSTALLED, status.reason)
            assertFalse(status.runtimeInstalled)
            assertFalse(status.cudaAvailable)
            assertEquals(0, calls.get())
        }
    }

    @Test
    fun `explicit requests expose fresh actual runtime metadata and total memory independently of selected device`() {
        installSpeechFixture(directory, "applio-runtime")
        val settings = SettingsStore(directory)
        val calls = AtomicInteger()
        val download = SpeechDownloadClient { _, _, _ -> throw SpeechInferenceFailedException() }
        speechCatalog(directory, download).use { catalog ->
            val component = SpeechComputeComponent(settings, catalog) { runtime ->
                assertEquals(directory.resolve("speech/applio-runtime"), runtime)
                calls.incrementAndGet()
                SpeechComputeProbeResult(true, "Synthetic GPU", 8585216000, "2.11.0+cu128", "12.8")
            }
            assertEquals(0, calls.get())
            val first = component.status()
            assertEquals(SpeechComputeDevice.CPU, first.selectedDevice)
            assertEquals(SpeechComputeReason.READY, first.reason)
            assertTrue(first.cudaAvailable)
            assertEquals(8585216000, first.gpuMemoryBytes)
            assertEquals("Synthetic GPU", first.gpuName)
            assertEquals("2.11.0+cu128", first.torchVersion)
            settings.update { it.copy(voiceConversionDevice = SpeechComputeDevice.CUDA) }
            assertEquals(SpeechComputeDevice.CUDA, component.status().selectedDevice)
            assertEquals(2, calls.get())
        }
    }

    @Test
    fun `CPU remains selected without CUDA and probe errors do not expose private process details`() {
        installSpeechFixture(directory, "applio-runtime")
        val download = SpeechDownloadClient { _, _, _ -> throw SpeechInferenceFailedException() }
        speechCatalog(directory, download).use { catalog ->
            val settings = SettingsStore(directory)
            val unavailable = SpeechComputeComponent(settings, catalog) {
                SpeechComputeProbeResult(false, "", 0, "2.11.0+cpu", "")
            }.status()
            assertEquals(SpeechComputeReason.CUDA_UNAVAILABLE, unavailable.reason)
            assertEquals(SpeechComputeDevice.CPU, unavailable.selectedDevice)
            val failed = SpeechComputeComponent(settings, catalog) { throw SpeechInferenceFailedException() }.status()
            assertEquals(SpeechComputeReason.PROBE_FAILED, failed.reason)
            assertEquals("", failed.gpuName)
            assertEquals("", failed.torchVersion)
        }
    }

    @Test
    fun `cancelled probe preserves interruption instead of returning a successful capability response`() {
        installSpeechFixture(directory, "applio-runtime")
        val download = SpeechDownloadClient { _, _, _ -> throw SpeechInferenceFailedException() }
        speechCatalog(directory, download).use { catalog ->
            val component = SpeechComputeComponent(SettingsStore(directory), catalog) { throw InterruptedException() }
            try {
                assertFailsWith<InterruptedException> { component.status() }
                assertTrue(Thread.currentThread().isInterrupted)
            } finally {
                Thread.interrupted()
            }
        }
    }
}
