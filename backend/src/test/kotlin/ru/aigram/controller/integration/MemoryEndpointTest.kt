package ru.aigram.controller.integration

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.AiTestServices
import ru.aigram.ai.EngineManager
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.memory.MemoryDocument
import ru.aigram.domain.memory.MemoryNotesUpdate
import ru.aigram.domain.memory.MemoryStatus
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

class MemoryEndpointTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `protects memory files validates edits detects conflicts and queues rebuilds without sending`() {
        val settings = SettingsStore(directory)
        val gateway = MemoryTelegramGateway()
        val ollama = AiTestServices("http://127.0.0.1:1")
        EngineManager(directory, ollama.runtime).use { engine ->
            ReplyCoordinator(settings, gateway) { _, _ -> ReplyResult("test", 0, 0.0) }.use { replies ->
                TestWebServerFactory().create(directory, settings, gateway, engine, ollama, replies, "session") {
                }.use { server ->
                    val base = server.start().substringBefore("/?")
                    val http = HttpClient.newHttpClient()
                    fun request(
                        path: String,
                        method: String = "GET",
                        body: String = "",
                        authenticated: Boolean = true,
                    ): HttpResponse<String> {
                        val builder = HttpRequest.newBuilder(URI.create(base + path))
                            .method(
                                method,
                                if (body.isEmpty()) {
                                    HttpRequest.BodyPublishers.noBody()
                                } else {
                                    HttpRequest.BodyPublishers.ofString(
                                        body,
                                    )
                                },
                            )
                        if (authenticated) {
                            builder.header("Cookie", "tiho=session")
                        }
                        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
                    }
                    assertEquals(401, request("/api/memory/42", authenticated = false).statusCode())
                    assertEquals(400, request("/api/memory/999").statusCode())
                    val document = Json.decodeFromString<MemoryDocument>(request("/api/memory/42").body())
                    val edit = Json.encodeToString(MemoryNotesUpdate("Дружим со школы", document.revision))
                    assertEquals(200, request("/api/memory/42", "PATCH", edit).statusCode())
                    assertEquals(409, request("/api/memory/42", "PATCH", edit).statusCode())
                    val changed = Json.decodeFromString<MemoryDocument>(request("/api/memory/42").body())
                    assertEquals("Дружим со школы", changed.notes)
                    assertEquals(
                        400,
                        request(
                            "/api/memory/42",
                            "PATCH",
                            Json.encodeToString(MemoryNotesUpdate("x".repeat(16001), changed.revision)),
                        ).statusCode(),
                    )
                    val queued = Json.decodeFromString<MemoryDocument>(
                        request("/api/memory/42", "POST", """{"rebuild":true}""").body(),
                    )
                    assertEquals(MemoryStatus.QUEUED, queued.status)
                    assertEquals(400, request("/api/memory/42", "POST", """{"immediate":"true"}""").statusCode())
                    assertEquals("Дружим со школы", queued.notes)
                    val job = Json.decodeFromString<MemoryDocument>(
                        request("/api/memory/42", "POST", """{"immediate":true}""").body(),
                    )
                    assertTrue(job.collection.active)
                    assertEquals(401, request("/api/memory/42/cancel", "POST", authenticated = false).statusCode())
                    val cancelled = Json.decodeFromString<MemoryDocument>(
                        request("/api/memory/42/cancel", "POST").body(),
                    )
                    assertFalse(cancelled.collection.active)
                    assertEquals("Дружим со школы", cancelled.notes)
                    assertEquals(200, request("/api/memory/cancel", "POST").statusCode())
                    assertTrue(
                        Files.readString(directory.resolve("memory/100/42/MEMORY.md")).contains("Дружим со школы"),
                    )
                    assertFalse(request("/api/memory/43").body().contains("Дружим со школы"))
                    assertEquals(400, request("/api/settings", "PATCH", """{"memoryIntervalHours":0}""").statusCode())
                    assertEquals(400, request("/api/settings", "PATCH", """{"memoryIntervalHours":"6"}""").statusCode())
                    assertEquals(
                        200,
                        request(
                            "/api/settings",
                            "PATCH",
                            """{"memoryEnabled":false,"memoryIntervalHours":12}""",
                        ).statusCode(),
                    )
                    assertEquals(12, settings.get().memoryIntervalHours)
                    assertFalse(settings.get().memoryEnabled)
                }
            }
        }
    }
}
