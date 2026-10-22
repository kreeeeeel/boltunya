package ru.aigram.controller.integration

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.AiTestServices
import ru.aigram.ai.EngineManager
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.system.storage.StorageLocationReport
import ru.aigram.memory.MemoryTelegramGateway
import ru.aigram.storage.SettingsStore
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StorageLocationsEndpointTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `protects file locations while retired error routes cannot read write or clear legacy files`() {
        val legacy = Files.createDirectories(directory.resolve("diagnostics")).resolve("errors.json")
        val content = "Synthetic unparsed legacy data"
        Files.writeString(legacy, content)
        val settings = SettingsStore(directory)
        val telegram = MemoryTelegramGateway()
        val ollama = AiTestServices("http://127.0.0.1:1")
        EngineManager(directory, ollama.runtime).use { engine ->
            ReplyCoordinator(settings, telegram) { _, _ -> ReplyResult("unused", 0, 0.0) }.use { replies ->
                TestWebServerFactory().create(directory, settings, telegram, engine, ollama, replies, "session") {}
                    .use { server ->
                        val base = server.start().substringBefore("/?")
                        val http = HttpClient.newHttpClient()
                        fun request(
                            path: String,
                            method: String = "GET",
                            authorized: Boolean = true,
                        ): HttpResponse<String> {
                            val builder = HttpRequest.newBuilder(URI.create(base + path))
                                .method(method, HttpRequest.BodyPublishers.noBody())
                            if (authorized) {
                                builder.header("Cookie", "tiho=session")
                            }
                            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
                        }

                        assertEquals(401, request("/api/storage/locations", authorized = false).statusCode())
                        val response = request("/api/storage/locations")
                        assertEquals(200, response.statusCode())
                        val locations = Json.decodeFromString<StorageLocationReport>(response.body()).locations
                        assertTrue(locations.any { it.id == "memory" })
                        assertTrue(locations.any { it.id == "application" })
                        assertTrue(locations.any { it.id == "speech" })
                        assertFalse(locations.any { it.id == "diagnostics" })
                        assertEquals(404, request("/api/diagnostics/errors").statusCode())
                        assertEquals(404, request("/api/diagnostics/errors", "DELETE").statusCode())
                    }
            }
        }
        assertEquals(content, Files.readString(legacy))
    }
}
