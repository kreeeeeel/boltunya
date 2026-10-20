package ru.boltunya.http

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.ai.AiTestServices
import ru.boltunya.ai.ComputeDeviceDiscovery
import ru.boltunya.ai.EngineManager
import ru.boltunya.ai.OllamaDeviceSelector
import ru.boltunya.ai.OllamaInstallation
import ru.boltunya.automation.FakeTelegramGateway
import ru.boltunya.automation.ReplyCoordinator
import ru.boltunya.domain.ComputeDevice
import ru.boltunya.domain.ComputeDeviceStatus
import ru.boltunya.domain.ReplyResult
import ru.boltunya.resources.ResourceCatalog
import ru.boltunya.storage.SettingsStore
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ComputeDeviceEndpointTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `protects device discovery saves valid preferences and leaves an external Ollama running`() {
        val upstream = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        upstream.createContext("/api/tags") { exchange ->
            val bytes = """{"models":[]}""".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        upstream.start()
        try {
            val endpoint = "http://127.0.0.1:${upstream.address.port}"
            val settings = SettingsStore(directory)
            val uuid = "GPU-11111111-1111-1111-1111-111111111111"
            val discovered = ComputeDeviceDiscovery {
                listOf(
                    ComputeDevice(
                        uuid,
                        "NVIDIA",
                        "nvidia",
                        12288L * 1024 * 1024,
                    ),
                )
            }
            val selector = OllamaDeviceSelector(settings, discovered, ResourceCatalog())
            val gateway = FakeTelegramGateway()
            val ai = AiTestServices(endpoint)
            EngineManager(
                directory,
                ai.runtime,
                installation = OllamaInstallation(directory, emptyMap()),
                devices = selector,
            ).use { engine ->
                ReplyCoordinator(settings, gateway) { _, _ -> ReplyResult("unused", 0, 0.0) }.use { replies ->
                    TestWebServerFactory().create(
                        directory,
                        settings,
                        gateway,
                        engine,
                        ai,
                        replies,
                        "session",
                        devices = discovered,
                    ) {}.use { server ->
                        val base = server.start().substringBefore("/?")
                        val http = HttpClient.newHttpClient()
                        fun request(
                            path: String,
                            method: String = "GET",
                            body: String = "",
                            authorized: Boolean = true,
                        ): HttpResponse<String> {
                            val builder = HttpRequest.newBuilder(URI.create(base + path))
                                .method(method, HttpRequest.BodyPublishers.ofString(body))
                            if (authorized) builder.header("Cookie", "tiho=session")
                            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
                        }
                        assertEquals(401, request("/api/engine/devices", authorized = false).statusCode())
                        val capability = Json.decodeFromString<ComputeDeviceStatus>(
                            request("/api/engine/devices").body(),
                        )
                        assertFalse(capability.selectionSupported)
                        assertEquals("external", capability.limitation)
                        assertNull(capability.active)
                        assertEquals(400, request("/api/settings", "PATCH", """{"ollamaDevice":0}""").statusCode())
                        assertEquals(400, request("/api/settings", "PATCH", """{"ollamaDevice":"0"}""").statusCode())
                        assertEquals(
                            400,
                            request("/api/settings", "PATCH", """{"ollamaDevice":"PCI-10DE"}""").statusCode(),
                        )
                        assertEquals(200, request("/api/settings", "PATCH", """{"ollamaDevice":"cpu"}""").statusCode())
                        assertEquals("cpu", SettingsStore(directory).get().ollamaDevice)
                        assertEquals(
                            200,
                            request("/api/settings", "PATCH", """{"ollamaDevice":"$uuid"}""").statusCode(),
                        )
                        assertEquals(uuid, settings.get().ollamaDevice)
                        assertEquals(200, request("/api/engine/start", "POST").statusCode())
                        assertFalse(engine.devices().managed)
                    }
                }
            }
            val alive = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("$endpoint/api/tags")).build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(200, alive.statusCode())
        } finally {
            upstream.stop(0)
        }
    }
}
