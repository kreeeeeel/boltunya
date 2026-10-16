package ru.boltunya.ai

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.exception.IntegrationException
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegistryModelInstallerTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `installs verified registry blobs and rejects a corrupted download`() {
        val bytes = "test weights".toByteArray()
        val digest = "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var corrupted = false
        server.createContext("/") { exchange ->
            val response = if (exchange.requestURI.path.contains("manifests")) {
                """{"config":{"digest":"$digest","size":${bytes.size}},"layers":[]}""".toByteArray()
            } else if (corrupted) {
                "corrupted".toByteArray()
            } else {
                bytes
            }
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val registry = "http://127.0.0.1:${server.address.port}"
            val installer = RegistryModelInstaller(directory.resolve("good"), registry)
            var progress = 0
            installer.install("qwen3:0.6b") { progress = it }
            assertEquals(100, progress)
            assertTrue(Files.exists(directory.resolve("good/manifests/registry.ollama.ai/library/qwen3/0.6b")))
            installer.install("qwen2.5:7b") {}
            assertTrue(Files.exists(directory.resolve("good/manifests/registry.ollama.ai/library/qwen2.5/7b")))
            installer.install("gemma3:4b") {}
            assertTrue(Files.exists(directory.resolve("good/manifests/registry.ollama.ai/library/gemma3/4b")))
            corrupted = true
            assertFailsWith<IntegrationException> {
                RegistryModelInstaller(directory.resolve("bad"), registry).install("qwen3:0.6b") {}
            }
            assertFalse(Files.exists(directory.resolve("bad/manifests/registry.ollama.ai/library/qwen3/0.6b")))
        } finally {
            server.stop(0)
        }
    }
}
