package ru.boltunya.http

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.ai.AiTestServices
import ru.boltunya.ai.EngineManager
import ru.boltunya.automation.FakeTelegramGateway
import ru.boltunya.automation.ReplyCoordinator
import ru.boltunya.domain.ChatRule
import ru.boltunya.domain.ReplyResult
import ru.boltunya.storage.SettingsStore
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActivityEndpointTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `activity details and queue mutations require local session and preserve queued work on clear`() {
        val settings = SettingsStore(directory)
        settings.saveRule("42", ChatRule(enabled = true))
        settings.update { it.copy(paused = false) }
        val gateway = FakeTelegramGateway()
        val ai = AiTestServices("http://127.0.0.1:1")
        EngineManager(directory, ai.runtime).use { engine ->
            ReplyCoordinator(settings, gateway) { _, _ -> ReplyResult("Ответ", 1, 1.0) }.use { replies ->
                replies.pauseQueue(true)
                replies.onMessage(gateway.messages.first())
                val id = replies.activities().single().id
                TestWebServerFactory().create(directory, settings, gateway, engine, ai, replies, "test-session") {
                }.use { server ->
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
                        if (authorized) builder.header("Cookie", "tiho=test-session")
                        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
                    }
                    assertEquals(401, request("/api/activity/$id", authorized = false).statusCode())
                    assertEquals(401, request("/api/activity/$id/cancel", "POST", authorized = false).statusCode())
                    assertEquals(200, request("/api/activity/$id").statusCode())
                    assertTrue(request("/api/activity/$id").body().contains("Привет"))
                    assertEquals(404, request("/api/activity/missing").statusCode())
                    assertEquals(400, request("/api/activity/queue", "PATCH", """{"paused":"yes"}""").statusCode())
                    assertEquals(200, request("/api/activity/$id/priority", "POST").statusCode())
                    assertTrue(replies.activities().single().priority)
                    assertTrue(request("/api/activity/$id").body().contains("\"priority\":true"))
                    assertEquals(200, request("/api/activity", "DELETE").statusCode())
                    assertEquals(1, replies.queue().queued)
                    assertEquals(200, request("/api/activity/queue", "PATCH", """{"paused":true}""").statusCode())
                    assertTrue(replies.queue().paused)
                    assertEquals(200, request("/api/activity/$id/cancel", "POST").statusCode())
                    assertEquals("cancelled", replies.activities().single().status)
                    assertEquals(200, request("/api/activity", "DELETE").statusCode())
                    assertTrue(replies.activities().isEmpty())
                    assertTrue(gateway.sent.isEmpty())
                }
            }
        }
    }
}
