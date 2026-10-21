package ru.aigram.controller.integration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.AiTestServices
import ru.aigram.ai.EngineManager
import ru.aigram.ai.profile.ModelRoutingStore
import ru.aigram.ai.profile.OpenAiStubServer
import ru.aigram.automation.FakeTelegramGateway
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.storage.SettingsStore
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
                        if (authenticated) {
                            builder.header("Cookie", "tiho=profiles-session")
                        }
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
                        val profile = Json.parseToJsonElement(created.body()).jsonObject
                        assertEquals("custom", profile["preset"]?.jsonPrimitive?.content)
                        assertEquals("true", profile["enabled"]?.jsonPrimitive?.content)
                        assertEquals(
                            "2048",
                            profile["options"]?.jsonObject?.get("maxOutputTokens")?.jsonPrimitive?.content,
                        )
                        assertEquals(
                            "8192",
                            profile["options"]?.jsonObject?.get("contextWindow")?.jsonPrimitive?.content,
                        )
                        assertEquals(
                            "true",
                            profile["capabilities"]?.jsonObject?.get("streaming")?.jsonPrimitive?.content,
                        )
                        val updated = request(
                            "/api/model-profiles/$id",
                            "PUT",
                            """{"name":"Updated API","provider":"openai-compatible","baseUrl":"${provider.baseUrl}",
                                |"model":"stub","options":{"contextWindow":32768,"temperature":null},
                                |"capabilities":{"vision":true},"apiKey":null}
                            """.trimMargin(),
                        )
                        assertEquals(200, updated.statusCode())
                        val partial = Json.parseToJsonElement(updated.body()).jsonObject
                        assertEquals("true", partial["apiKeyConfigured"]?.jsonPrimitive?.content)
                        assertEquals(
                            "32768",
                            partial["options"]?.jsonObject?.get("contextWindow")?.jsonPrimitive?.content,
                        )
                        assertEquals(
                            "2048",
                            partial["options"]?.jsonObject?.get("maxOutputTokens")?.jsonPrimitive?.content,
                        )
                        assertEquals("true", partial["capabilities"]?.jsonObject?.get("vision")?.jsonPrimitive?.content)
                        assertEquals(
                            "true",
                            partial["capabilities"]?.jsonObject?.get("streaming")?.jsonPrimitive?.content,
                        )
                        val listed = request("/api/model-profiles", "GET")
                        assertTrue(listed.body().contains("apiKeyConfigured"))
                        assertFalse(listed.body().contains("private-stub-key"))
                        assertEquals(400, request("/api/model-profiles/$id/test", "POST", "{}").statusCode())
                        assertEquals(0, provider.requests.get())
                        assertEquals(
                            200,
                            request(
                                "/api/model-routing",
                                "PUT",
                                """{"enabled":true,"replyModel":"qwen3:1.7b","memoryModel":"gemma3:4b",
                                    |"researchModel":"qwen2.5:7b","visionModel":"qwen3-vl:2b"}
                                """.trimMargin(),
                            ).statusCode(),
                        )
                        assertEquals("qwen3-vl:2b", ModelRoutingStore(directory).get().visionModel)
                        assertTrue(request("/api/model-routing", "GET").body().contains("gemma3:4b"))
                        assertEquals(
                            400,
                            request(
                                "/api/model-routing",
                                "PUT",
                                """{"replyProfileId":"$id","replyModel":"qwen3:1.7b"}""",
                            ).statusCode(),
                        )
                        assertEquals(
                            400,
                            request("/api/model-routing", "PUT", """{"visionModel":"gemma3:1b"}""").statusCode(),
                        )
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
