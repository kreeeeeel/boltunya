package ru.boltunya.http

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.ai.AiTestServices
import ru.boltunya.ai.EngineManager
import ru.boltunya.automation.FakeTelegramGateway
import ru.boltunya.automation.ReplyCoordinator
import ru.boltunya.domain.ReplyResult
import ru.boltunya.storage.SettingsStore
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelAutomationValidationTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `manual model selection stays available but global and per chat automation are rejected`() {
        val settings = SettingsStore(directory)
        val gateway = FakeTelegramGateway()
        val services = AiTestServices("http://127.0.0.1:1")
        EngineManager(directory, services.runtime).use { engine ->
            ReplyCoordinator(settings, gateway) { _, _ -> ReplyResult("test", 0, 0.0) }.use { coordinator ->
                TestWebServerFactory().create(
                    directory,
                    settings,
                    gateway,
                    engine,
                    services,
                    coordinator,
                    "session",
                ) {}.use { server ->
                    val base = server.start().substringBefore("/?")
                    val http = HttpClient.newHttpClient()
                    fun request(path: String, method: String, body: String): Int {
                        val request = HttpRequest.newBuilder(URI.create(base + path))
                            .header("Cookie", "tiho=session")
                            .method(method, HttpRequest.BodyPublishers.ofString(body)).build()
                        return http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()
                    }
                    assertEquals(
                        200,
                        request("/api/settings", "PATCH", """{"defaultModel":"qwen3:0.6b","paused":true}"""),
                    )
                    assertEquals(400, request("/api/settings", "PATCH", """{"paused":false}"""))
                    assertTrue(settings.get().paused)
                    assertEquals(
                        400,
                        request(
                            "/api/chats/42/rule",
                            "PUT",
                            """{"model":"qwen3:0.6b","useDefaultModel":false,"enabled":true}""",
                        ),
                    )
                    assertFalse(settings.rule("42").enabled)
                    assertTrue(gateway.sent.isEmpty())
                }
            }
        }
    }
}
