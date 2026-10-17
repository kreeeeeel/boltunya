package ru.boltunya.ai

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OllamaModelInventoryTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `finds complete local models without running an Ollama server`() {
        manifest("library/qwen3/1.7b")
        manifest("community/custom/latest")
        assertEquals(setOf("qwen3:1.7b", "community/custom:latest"), OllamaModelInventory(directory).installed())
    }

    @Test
    fun `ignores missing truncated and partial model files`() {
        val manifest = manifest("library/qwen3/1.7b")
        val blob = directory.resolve("blobs/sha256-" + "b".repeat(64))
        Files.writeString(blob, "cut")
        assertTrue(OllamaModelInventory(directory).installed().isEmpty())
        Files.delete(blob)
        assertTrue(OllamaModelInventory(directory).installed().isEmpty())
        Files.writeString(blob, "weights")
        Files.move(manifest, manifest.resolveSibling("1.7b.partial"))
        assertTrue(OllamaModelInventory(directory).installed().isEmpty())
    }

    @Test
    fun `does not confuse another registry or malformed manifests with installed library models`() {
        val manifest = manifest("library/qwen3/1.7b")
        val foreign = directory.resolve("manifests/example.test/library/qwen3/1.7b")
        Files.createDirectories(foreign.parent)
        Files.move(manifest, foreign)
        assertTrue(OllamaModelInventory(directory).installed().isEmpty())
        Files.writeString(manifest, """{"config":{"digest":{},"size":6},"layers":[{}]}""")
        assertTrue(OllamaModelInventory(directory).installed().isEmpty())
    }

    private fun manifest(name: String): Path {
        val config = "a".repeat(64)
        val weights = "b".repeat(64)
        val blobs = directory.resolve("blobs")
        Files.createDirectories(blobs)
        Files.writeString(blobs.resolve("sha256-$config"), "config")
        Files.writeString(blobs.resolve("sha256-$weights"), "weights")
        val manifest = directory.resolve("manifests/registry.ollama.ai/$name")
        Files.createDirectories(manifest.parent)
        Files.writeString(
            manifest,
            """{"config":{"digest":"sha256:$config","size":6},"layers":[{"digest":"sha256:$weights","size":7}]}""",
        )
        return manifest
    }
}
