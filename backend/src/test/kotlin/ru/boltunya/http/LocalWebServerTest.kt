package ru.boltunya.http

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.ai.EngineManager
import ru.boltunya.ai.OllamaClient
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

class LocalWebServerTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `rejects unauthenticated and cross origin requests and validates credentials`() {
        val settings = SettingsStore(directory)
        val gateway = FakeTelegramGateway()
        val ollama = OllamaClient("http://127.0.0.1:1")
        val engine = EngineManager(directory, ollama)
        val coordinator = ReplyCoordinator(settings, gateway) { _, _ -> ReplyResult("test", 0, 0.0) }
        TestWebServerFactory().create(directory, settings, gateway, engine, ollama, coordinator, "test-session") {
        }.use { server ->
            val base = server.start().substringBefore("/?")
            val http = HttpClient.newHttpClient()
            fun post(body: String, authenticated: Boolean, origin: String? = null): HttpResponse<String> {
                val builder = HttpRequest.newBuilder(URI.create("$base/api/telegram/connect"))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                if (authenticated) {
                    builder.header("Cookie", "tiho=test-session")
                }
                if (origin != null) {
                    builder.header("Origin", origin)
                }
                return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            }
            assertEquals(401, post("{}", false).statusCode())
            assertEquals(403, post("{}", true, "https://untrusted.test").statusCode())
            val invalid = post("""{"apiId":0,"apiHash":"bad","phone":"123"}""", true)
            assertEquals(400, invalid.statusCode())
            assertFalse(invalid.body().contains("stackTrace"))
        }
        coordinator.close()
        engine.close()
    }

    @Test
    fun `protects media requests and validates shared prompt updates`() {
        val settings = SettingsStore(directory)
        val gateway = FakeTelegramGateway()
        val ollama = OllamaClient("http://127.0.0.1:1")
        val engine = EngineManager(directory, ollama)
        val coordinator = ReplyCoordinator(settings, gateway) { _, _ -> ReplyResult("test", 0, 0.0) }
        TestWebServerFactory().create(directory, settings, gateway, engine, ollama, coordinator, "test-session") {
        }.use { server ->
            val base = server.start().substringBefore("/?")
            val http = HttpClient.newHttpClient()
            val media = HttpRequest.newBuilder(URI.create("$base/api/media/1")).build()
            assertEquals(401, http.send(media, HttpResponse.BodyHandlers.ofString()).statusCode())
            fun update(prompt: String, model: String = "gemma3:4b"): Int {
                val request = HttpRequest.newBuilder(URI.create("$base/api/settings"))
                    .header("Cookie", "tiho=test-session")
                    .method(
                        "PATCH",
                        HttpRequest.BodyPublishers.ofString(
                            "{\"globalPrompt\":\"$prompt\",\"defaultModel\":\"$model\"}",
                        ),
                    )
                    .build()
                return http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()
            }
            assertEquals(200, update("Shared owner instructions"))
            assertEquals("Shared owner instructions", settings.get().globalPrompt)
            assertEquals("gemma3:4b", settings.get().defaultModel)
            assertEquals(400, update("", "unknown:model"))
            assertEquals(400, update("x".repeat(4001)))
            assertEquals(200, update(""))
            assertEquals("", settings.get().globalPrompt)
        }
        coordinator.close()
        engine.close()
    }

    @Test
    fun `validates automation preferences and rejects removed window route`() {
        val settings = SettingsStore(directory)
        val gateway = FakeTelegramGateway()
        val ollama = OllamaClient("http://127.0.0.1:1")
        val engine = EngineManager(directory, ollama)
        val coordinator = ReplyCoordinator(settings, gateway) { _, _ -> ReplyResult("test", 0, 0.0) }
        TestWebServerFactory().create(
            directory,
            settings,
            gateway,
            engine,
            ollama,
            coordinator,
            "test-session",
        ) {}.use { server ->
            val base = server.start().substringBefore("/?")
            val http = HttpClient.newHttpClient()
            fun request(path: String, method: String, body: String, authenticated: Boolean = true): Int {
                val builder = HttpRequest.newBuilder(URI.create(base + path))
                    .method(method, HttpRequest.BodyPublishers.ofString(body))
                if (authenticated) builder.header("Cookie", "tiho=test-session")
                return http.send(builder.build(), HttpResponse.BodyHandlers.discarding()).statusCode()
            }
            assertEquals(400, request("/api/settings", "PATCH", """{"replyDelaySeconds":-1}"""))
            assertEquals(400, request("/api/settings", "PATCH", """{"replyIntervalSeconds":121}"""))
            assertEquals(
                200,
                request(
                    "/api/settings",
                    "PATCH",
                    """{
                "replyDelaySeconds":7,"replyIntervalSeconds":10,"showTyping":false,
                "launchAtLogin":true,"resumeOnStartup":true,"closeToTray":false
            }""",
                ),
            )
            assertEquals(7, settings.get().replyDelaySeconds)
            assertEquals(10, settings.get().replyIntervalSeconds)
            assertFalse(settings.get().showTyping)
            assertEquals(true, settings.get().launchAtLogin)
            assertEquals(401, request("/api/window", "POST", """{"action":"close"}""", false))
            assertEquals(404, request("/api/window", "POST", """{"action":"minimize"}"""))
        }
        coordinator.close()
        engine.close()
    }
}
