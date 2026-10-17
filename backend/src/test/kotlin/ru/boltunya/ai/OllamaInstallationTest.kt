package ru.boltunya.ai

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OllamaInstallationTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `detects a user installation added after the application has started`() {
        val local = directory.resolve("local")
        val installation = OllamaInstallation(directory.resolve("app"), mapOf("LOCALAPPDATA" to local.toString()))
        assertNull(installation.executable())
        val executable = local.resolve("Programs/Ollama/ollama.exe")
        Files.createDirectories(executable.parent)
        Files.writeString(executable, "runtime")
        assertEquals(executable, installation.executable())
    }

    @Test
    fun `prefers the bundled engine and otherwise recognizes Program Files and mixed case Path`() {
        val system = directory.resolve("programs")
        val executable = system.resolve("Ollama/ollama.exe")
        Files.createDirectories(executable.parent)
        Files.writeString(executable, "runtime")
        val installation = OllamaInstallation(directory, mapOf("ProgramFiles" to system.toString()))
        assertEquals(executable, installation.executable())
        val bundled = directory.resolve("engine/ollama.exe")
        Files.createDirectories(bundled.parent)
        Files.writeString(bundled, "bundled")
        assertEquals(bundled, installation.executable())
        assertEquals(
            executable,
            OllamaInstallation(directory.resolve("other"), mapOf("Path" to executable.parent.toString())).executable(),
        )
    }

    @Test
    fun `an empty Path and a directory named ollama exe are not an installation`() {
        Files.createDirectories(directory.resolve("engine/ollama.exe"))
        assertNull(OllamaInstallation(directory, mapOf("PATH" to "")).executable())
    }
}
