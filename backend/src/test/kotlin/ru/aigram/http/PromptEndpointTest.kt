package ru.aigram.http

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.AiTestServices
import ru.aigram.ai.EngineManager
import ru.aigram.automation.FakeTelegramGateway
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ReplyResult
import ru.aigram.storage.SettingsStore
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromptEndpointTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `model instructions persist and oversize budgets are reported without an inference request`() {
        val settings = SettingsStore(directory)
        val gateway = FakeTelegramGateway()
        val ai = AiTestServices("http://127.0.0.1:1")
        EngineManager(directory, ai.runtime).use { engine ->
            ReplyCoordinator(settings, gateway) { _, _ -> ReplyResult("unused", 0, 0.0) }.use { replies ->
                TestWebServerFactory().create(directory, settings, gateway, engine, ai, replies, "prompt-session") {
                }.use { server ->
                    val base = server.start().substringBefore("/?")
                    val http = HttpClient.newHttpClient()
                    fun request(path: String, method: String, body: String = "{}", authenticated: Boolean = true) =
                        http.send(
                            HttpRequest.newBuilder(URI.create(base + path))
                                .method(method, HttpRequest.BodyPublishers.ofString(body))
                                .header("Cookie", if (authenticated) "tiho=prompt-session" else "")
                                .build(),
                            HttpResponse.BodyHandlers.ofString(),
                        )
                    assertEquals(401, request("/api/model-prompts", "GET", authenticated = false).statusCode())
                    val preferences = """{"model":"qwen3:1.7b","instructions":"Только проверенные факты",
                        "contextWindow":512,"tokenEncoding":"cl100k"}
                    """.trimIndent()
                    assertEquals(200, request("/api/model-prompts", "PUT", preferences).statusCode())
                    assertTrue(request("/api/model-prompts", "GET").body().contains("Только проверенные факты"))
                    val result = request(
                        "/api/prompts/budget",
                        "POST",
                        """{"rule":{"model":"qwen3:1.7b","useDefaultModel":false}}""",
                    )
                    assertEquals(200, result.statusCode(), result.body())
                    val report = Json.parseToJsonElement(result.body()).jsonObject
                    assertEquals(512, report.getValue("contextWindow").jsonPrimitive.int)
                    assertTrue(report.getValue("systemTokens").jsonPrimitive.int > 0)
                    assertTrue(report.getValue("reservedOutputTokens").jsonPrimitive.int > 0)
                    assertFalse(report.getValue("fits").jsonPrimitive.boolean)
                    assertFalse(report.getValue("includesHistory").jsonPrimitive.boolean)
                    assertTrue(report.getValue("estimated").jsonPrimitive.boolean)
                    assertEquals(
                        400,
                        request(
                            "/api/model-prompts",
                            "PUT",
                            """{"model":"qwen3:1.7b","contextWindow":1000000}""",
                        ).statusCode(),
                    )
                    assertEquals(
                        400,
                        request("/api/model-prompts", "PUT", """{"model":"not-a-model"}""").statusCode(),
                    )
                    assertTrue(gateway.sent.isEmpty())
                }
            }
        }
    }
}
