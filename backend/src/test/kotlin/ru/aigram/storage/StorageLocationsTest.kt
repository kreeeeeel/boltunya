package ru.aigram.storage

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.OllamaInstallation
import ru.aigram.configuration.ApplicationConfiguration
import ru.aigram.resources.ResourceCatalog
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StorageLocationsTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `reports actual application directories and configured model storage`() {
        val models = Files.createDirectories(directory.resolve("model storage"))
        Files.createDirectories(directory.resolve("engine"))
        Files.write(directory.resolve("engine/ollama.exe"), byteArrayOf(1))
        Files.createDirectories(directory.resolve("memory/100/42"))
        val locations = StorageLocations(
            ApplicationConfiguration(directory, false),
            OllamaInstallation(directory, emptyMap()),
            ResourceCatalog(),
            mapOf("OLLAMA_MODELS" to models.toString()),
            directory,
        ).report().locations.associateBy { it.id }
        assertEquals(directory.resolve("memory").toString(), locations.getValue("memory").path)
        assertEquals(models.toString(), locations.getValue("ollama-models").path)
        assertTrue(locations.getValue("ollama-models").openable)
        assertFalse(locations.getValue("telegram-cache").openable)
        assertTrue(locations.getValue("ollama-runtime").available)
        assertFalse(locations.containsKey("diagnostics"))
        assertFalse(Files.exists(directory.resolve("diagnostics")))
    }

    @Test
    fun `does not invent a host model path for Docker or remote Ollama`() {
        listOf("http://127.0.0.1:11434", "http://192.0.2.1:11434").stream().forEach { url ->
            val locations = StorageLocations(
                ApplicationConfiguration(directory, false, url),
                OllamaInstallation(directory, emptyMap()),
                ResourceCatalog(),
                emptyMap(),
                directory,
            ).report().locations.filter { it.id.startsWith("ollama-") }
            assertTrue(locations.all { it.kind == "external" && it.path.isEmpty() && !it.openable })
            assertTrue(locations.all { it.note.isNotBlank() })
        }
    }

    @Test
    fun `reports only the current profile speech folder and does not create missing directories`() {
        val service = StorageLocations(
            ApplicationConfiguration(directory, false),
            OllamaInstallation(directory, emptyMap()),
            ResourceCatalog(),
            emptyMap(),
            directory.resolve("external user profile"),
        )
        val missing = service.report().locations.single { it.id == "speech" }
        assertEquals(directory.resolve("speech").toAbsolutePath().normalize().toString(), missing.path)
        assertEquals("directory", missing.kind)
        assertFalse(missing.available)
        assertFalse(missing.openable)
        assertFalse(Files.exists(directory.resolve("speech")))

        Files.createDirectories(directory.resolve("speech/installed-model"))
        val installed = service.report().locations.single { it.id == "speech" }
        assertEquals(missing.path, installed.path)
        assertTrue(installed.available)
        assertTrue(installed.openable)
        assertTrue(installed.label.isNotBlank())
    }
}
