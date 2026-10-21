package ru.aigram.ai

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EngineDetectionTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `separates an installed stopped runtime from a missing runtime`() {
        val installation = OllamaInstallation(directory, emptyMap())
        val digest = "a".repeat(64)
        val blob = directory.resolve("blobs/sha256-$digest")
        Files.createDirectories(blob.parent)
        Files.writeString(blob, "x")
        val manifest = directory.resolve("manifests/registry.ollama.ai/library/qwen3/1.7b")
        Files.createDirectories(manifest.parent)
        Files.writeString(
            manifest,
            """{"config":{"digest":"sha256:$digest","size":1},"layers":[{"digest":"sha256:$digest","size":1}]}""",
        )
        EngineManager(
            directory,
            OllamaRuntime("http://127.0.0.1:1"),
            installation = installation,
            localModels = OllamaModelInventory(directory),
        ).use { engine ->
            assertFalse(engine.status().installed)
            assertTrue(engine.status().models.single { it.id == "qwen3:1.7b" }.installed)
            val executable = directory.resolve("engine/ollama.exe")
            Files.createDirectories(executable.parent)
            Files.writeString(executable, "runtime")
            assertTrue(engine.status().installed)
            assertFalse(engine.status().online)
        }
    }

    @Test
    fun `refreshes existing models from a server and accepts fully qualified library names`() {
        val models = AtomicReference("""{"models":[]}""")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/tags") { exchange ->
            val bytes = models.get().toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            EngineManager(
                directory,
                OllamaRuntime("http://127.0.0.1:${server.address.port}"),
                installation = OllamaInstallation(directory, emptyMap()),
            ).use { engine ->
                assertTrue(engine.status().online)
                assertTrue(engine.status().installed)
                assertTrue(engine.status().models.none { it.installed })
                models.set("""{"models":[{"model":"registry.ollama.ai/library/qwen3:1.7b"}]}""")
                assertTrue(engine.status().models.single { it.id == "qwen3:1.7b" }.installed)
                models.set("""{"models":[]}""")
                assertTrue(engine.status().models.none { it.installed })
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `a custom or remote endpoint is never replaced by a local default server`() {
        assertFalse(OllamaRuntime("http://127.0.0.1:1").isDefaultLocalServer)
        assertFalse(OllamaRuntime("https://example.test:11434").isLocalServer)
        assertTrue(OllamaRuntime("http://localhost:11434").isDefaultLocalServer)
    }
}
