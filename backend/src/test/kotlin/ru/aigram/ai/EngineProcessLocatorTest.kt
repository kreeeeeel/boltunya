package ru.aigram.ai

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EngineProcessLocatorTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `recognizes an executable exposed through another filesystem path`() {
        val executable = Files.writeString(directory.resolve("ollama.exe"), "engine")
        val alias = Files.createLink(directory.resolve("virtualized.exe"), executable)
        assertTrue(EngineProcessLocator().matches(alias, executable))
    }

    @Test
    fun `does not adopt unrelated or removed executables`() {
        val executable = Files.writeString(directory.resolve("ollama.exe"), "engine")
        val foreign = Files.writeString(directory.resolve("foreign.exe"), "engine")
        val locator = EngineProcessLocator()
        assertFalse(locator.matches(foreign, executable))
        assertFalse(locator.matches(directory.resolve("removed.exe"), executable))
    }
}
