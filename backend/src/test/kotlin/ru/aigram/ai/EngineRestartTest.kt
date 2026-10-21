package ru.aigram.ai

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.exception.impl.EngineRestartUnavailableException
import ru.aigram.exception.impl.EngineUnavailableException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EngineRestartTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `restart stops the managed process before launching its replacement and closes the replacement`() {
        val executable = executable()
        val original = FakeEngineProcess(1)
        val replacement = FakeEngineProcess(2)
        val engine = EngineManager(
            directory,
            OllamaRuntime("http://127.0.0.1:11434"),
            installation = OllamaInstallation(directory, emptyMap()),
            startProcess = { builder ->
                assertFalse(original.isAlive)
                assertEquals(listOf(executable.toString(), "serve"), builder.command())
                replacement
            },
        )
        engine.use {
            manage(engine, original)
            engine.restart()
            assertFalse(original.isAlive)
            assertTrue(replacement.isAlive)
        }
        assertFalse(replacement.isAlive)
    }

    @Test
    fun `restart refuses an unmanaged or custom server without stopping any process`() {
        EngineManager(directory, OllamaRuntime("http://127.0.0.1:11434")).use { engine ->
            assertFailsWith<EngineRestartUnavailableException> { engine.restart() }
        }
        val external = FakeEngineProcess(1)
        EngineManager(directory, OllamaRuntime("http://127.0.0.1:1")).use { engine ->
            manage(engine, external)
            assertFailsWith<EngineRestartUnavailableException> { engine.restart() }
            assertTrue(external.isAlive)
        }
    }

    @Test
    fun `a missing executable leaves the existing managed process running`() {
        val original = FakeEngineProcess(1)
        EngineManager(
            directory,
            OllamaRuntime("http://127.0.0.1:11434"),
            installation = OllamaInstallation(directory, emptyMap()),
        ).use { engine ->
            manage(engine, original)
            assertFailsWith<EngineUnavailableException> { engine.restart() }
            assertTrue(original.isAlive)
        }
    }

    @Test
    fun `launch failure is reported and never leaves a stale managed process`() {
        executable()
        val original = FakeEngineProcess(1)
        EngineManager(
            directory,
            OllamaRuntime("http://127.0.0.1:11434"),
            installation = OllamaInstallation(directory, emptyMap()),
            startProcess = { throw IOException("launch failed") },
        ).use { engine ->
            manage(engine, original)
            assertFailsWith<EngineUnavailableException> { engine.restart() }
            assertFalse(original.isAlive)
            assertFailsWith<EngineRestartUnavailableException> { engine.restart() }
        }
    }

    private fun executable(): Path {
        val path = directory.resolve("engine/ollama.exe")
        Files.createDirectories(path.parent)
        Files.writeString(path, "test executable")
        return path
    }

    private fun manage(engine: EngineManager, process: ProcessHandle) {
        val field = EngineManager::class.java.getDeclaredField("managedServer")
        field.isAccessible = true
        field.set(engine, process)
    }
}
