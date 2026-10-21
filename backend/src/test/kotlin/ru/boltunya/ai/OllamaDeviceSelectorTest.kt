package ru.boltunya.ai

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.domain.ComputeDevice
import ru.boltunya.exception.impl.InvalidOllamaDeviceException
import ru.boltunya.storage.SettingsStore
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OllamaDeviceSelectorTest {
    @TempDir lateinit var directory: Path
    private val uuid = "GPU-11111111-1111-1111-1111-111111111111"

    @Test
    fun `validates discovered devices persists selection and retains a temporarily absent saved UUID`() {
        val settings = SettingsStore(directory)
        var discovered = listOf(ComputeDevice(uuid, "NVIDIA", "nvidia", 12288L * 1024 * 1024))
        val selector = OllamaDeviceSelector(settings, ComputeDeviceDiscovery { discovered })
        selector.validate("auto")
        selector.validate("cpu")
        selector.validate(uuid)
        assertFailsWith<InvalidOllamaDeviceException> { selector.validate("0") }
        assertFailsWith<InvalidOllamaDeviceException> { selector.validate("GPU-22222222-2222-2222-2222-222222222222") }
        settings.update { it.copy(ollamaDevice = uuid) }
        assertEquals(uuid, SettingsStore(directory).get().ollamaDevice)
        discovered = emptyList()
        selector.validate(uuid)
        assertEquals(uuid, selector.environment().getValue("CUDA_VISIBLE_DEVICES"))
    }

    @Test
    fun `restricts explicit modes to the intended backend and automatic mode preserves inherited settings`() {
        val selector =
            OllamaDeviceSelector(SettingsStore(directory), ComputeDeviceDiscovery { emptyList() })
        assertTrue(selector.environment("auto").isEmpty())
        val cpu = selector.environment("cpu")
        listOf(
            "CUDA_VISIBLE_DEVICES",
            "HIP_VISIBLE_DEVICES",
            "ROCR_VISIBLE_DEVICES",
            "GPU_DEVICE_ORDINAL",
            "GGML_VK_VISIBLE_DEVICES",
        )
            .stream().forEach { assertEquals("-1", cpu[it]) }
        assertEquals("0", cpu["OLLAMA_VULKAN"])
        val nvidia = selector.environment(uuid)
        assertEquals(uuid, nvidia["CUDA_VISIBLE_DEVICES"])
        assertEquals("-1", nvidia["HIP_VISIBLE_DEVICES"])
        assertEquals("-1", nvidia["GGML_VK_VISIBLE_DEVICES"])
        assertFailsWith<InvalidOllamaDeviceException> { selector.environment("0,1") }
    }

    @Test
    fun `reports pending app managed changes without claiming control over external or remote Ollama`() {
        val settings = SettingsStore(directory)
        settings.update { it.copy(ollamaDevice = "cpu") }
        val selector = OllamaDeviceSelector(settings, ComputeDeviceDiscovery { emptyList() })
        val owned = selector.status(true, true, true, true, true, "auto")
        assertTrue(owned.selectionSupported)
        assertTrue(owned.restartRequired)
        assertEquals("next-start", owned.limitation)
        assertEquals("auto", owned.active)
        val external = selector.status(true, true, true, true, false, null)
        assertFalse(external.selectionSupported)
        assertFalse(external.managed)
        assertNull(external.active)
        assertEquals("external", external.limitation)
        assertEquals("remote", selector.status(true, false, false, true, false, null).limitation)
        val stopped = selector.status(false, true, true, true, false, null)
        assertTrue(stopped.selectionSupported)
        assertFalse(stopped.restartRequired)
        assertEquals("missing-runtime", selector.status(false, true, true, false, false, null).limitation)
        assertTrue(stopped.autoUsesEnvironment)
        assertTrue(stopped.nvidiaSelectionOnly)
    }

    @Test
    fun `passes configured environment only into the app managed child process without changing this process`() {
        val settings = SettingsStore(directory)
        settings.update { it.copy(ollamaDevice = "cpu") }
        val selector = OllamaDeviceSelector(settings, ComputeDeviceDiscovery { emptyList() })
        val inherited = System.getenv("CUDA_VISIBLE_DEVICES")
        EngineManager(directory, OllamaRuntime("http://127.0.0.1:1"), devices = selector).use { engine ->
            val command = directory.resolve("engine/ollama.exe")
            val cpu = engine.prepareProcess(command)
            assertEquals(listOf(command.toString(), "serve"), cpu.command())
            assertEquals("-1", cpu.environment()["CUDA_VISIBLE_DEVICES"])
            assertEquals(inherited, System.getenv("CUDA_VISIBLE_DEVICES"))
            settings.update { it.copy(ollamaDevice = "auto") }
            assertEquals(inherited, engine.prepareProcess(command).environment()["CUDA_VISIBLE_DEVICES"])
        }
    }
}
