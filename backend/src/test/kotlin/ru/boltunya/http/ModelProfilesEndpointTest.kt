package ru.boltunya.http

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.ai.AiTestServices
import ru.boltunya.ai.EngineManager
import ru.boltunya.ai.profile.OpenAiStubServer
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

class ModelProfilesEndpointTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `profile CRUD requires session never returns keys and saving does not make a provider request`() {
        val settings = SettingsStore(directory)
        val gateway = FakeTelegramGateway()
        val ai = AiTestServices("http://127.0.0.1:1")
        EngineManager(directory, ai.runtime).use { engine ->
            ReplyCoordinator(settings, gateway) { _, _ -> ReplyResult("test", 0, 0.0) }.use { replies ->
                TestWebServerFactory().create(directory, settings, gateway, engine, ai, replies, "profiles-session") {
                }.use { server ->
                    val base = server.start().substringBefore("/?")
                    val http = HttpClient.newHttpClient()
                    fun request(
                        path: String,
                        method: String,
                        body: String = "{}",
                        authenticated: Boolean = true,
                    ): HttpResponse<String> {
                        val builder = HttpRequest.newBuilder(
                            URI.create(base + path),
                        ).method(method, HttpRequest.BodyPublishers.ofString(body))
                        if (authenticated) builder.header("Cookie", "tiho=profiles-session")
                        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
                    }
                    assertEquals(401, request("/api/model-profiles", "GET", authenticated = false).statusCode())
                    assertEquals(400, request("/api/settings", "PATCH", """{"maxParallelTasks":0}""").statusCode())
                    assertEquals(400, request("/api/settings", "PATCH", """{"maxParallelTasks":5}""").statusCode())
                    assertEquals(200, request("/api/settings", "PATCH", """{"maxParallelTasks":2}""").statusCode())
                    assertEquals(2, settings.get().maxParallelTasks)
                    OpenAiStubServer("").start().use { provider ->
                        val created =
                            request(
                                "/api/model-profiles",
                                "POST",
                                """
                                {"name":"API","provider":"openai-compatible","baseUrl":"${provider.baseUrl}",
                                "model":"stub","apiKey":"private-stub-key"}
                                """.trimIndent(),
                            )
                        assertEquals(200, created.statusCode())
                        assertFalse(created.body().contains("private-stub-key"))
                        val id = Json.parseToJsonElement(
                            created.body(),
                        ).jsonObject["id"]?.jsonPrimitive?.content.orEmpty()
                        assertTrue(id.isNotEmpty())
                        val listed = request("/api/model-profiles", "GET")
                        assertTrue(listed.body().contains("apiKeyConfigured"))
                        assertFalse(listed.body().contains("private-stub-key"))
                        assertEquals(400, request("/api/model-profiles/$id/test", "POST", "{}").statusCode())
                        assertEquals(0, provider.requests.get())
                        assertEquals(
                            200,
                            request("/api/settings", "PATCH", """{"defaultModelProfileId":"$id"}""").statusCode(),
                        )
                        assertEquals(409, request("/api/model-profiles/$id", "DELETE").statusCode())
                        assertEquals(
                            200,
                            request("/api/settings", "PATCH", """{"defaultModelProfileId":""}""").statusCode(),
                        )
                        assertEquals(200, request("/api/model-profiles/$id", "DELETE").statusCode())
                        assertEquals(404, request("/api/model-profiles/$id", "DELETE").statusCode())
                    }
                }
            }
        }
    }
}
