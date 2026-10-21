package ru.aigram.controller.integration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.AiTestServices
import ru.aigram.ai.EngineManager
import ru.aigram.automation.FakeTelegramGateway
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.chat.ReplyInterruptionMode
import ru.aigram.domain.settings.ReplyProcessingMode
import ru.aigram.storage.SettingsStore
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.util.Locale
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReplyPolicyEndpointTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `global policy accepts each wire value preserves omitted updates and rejects invalid changes atomically`() =
        withServer { settings, gateway, base ->
            ReplyProcessingMode.entries.stream().forEach { mode ->
                val value = mode.name.lowercase(Locale.ROOT)
                assertEquals(
                    200,
                    request(
                        base,
                        "/api/settings",
                        "PATCH",
                        """{"replyProcessingMode":"$value"}""",
                    ).statusCode(),
                )
                assertEquals(mode, settings.get().replyProcessingMode)
                assertEquals(value, stateSettings(base)["replyProcessingMode"]?.jsonPrimitive?.content)
            }
            assertEquals(200, request(base, "/api/settings", "PATCH", """{"closeToTray":false}""").statusCode())
            assertEquals(ReplyProcessingMode.EACH, settings.get().replyProcessingMode)
            assertEquals(ReplyProcessingMode.EACH, SettingsStore(directory).get().replyProcessingMode)
            val before = settings.get()
            Stream.of("null", "true", "0", "\"unknown\"", "\"inherit\"").forEach { value ->
                val response = request(
                    base,
                    "/api/settings",
                    "PATCH",
                    """{"replyProcessingMode":$value,"globalPrompt":"must not persist"}""",
                )
                assertEquals(400, response.statusCode())
                assertEquals(before, settings.get())
            }
            assertTrue(settings.get().paused)
            assertTrue(gateway.sent.isEmpty())
        }

    @Test
    fun `per chat policy defaults to inherit and persists all overrides without enabling AI`() =
        withServer { settings, gateway, base ->
            settings.update { it.copy(replyProcessingMode = ReplyProcessingMode.FINISH) }
            assertEquals(200, request(base, "/api/chats/42/rule", "PUT", """{"enabled":false}""").statusCode())
            assertEquals(ReplyInterruptionMode.INHERIT, settings.rule("42").replyInterruptionMode)
            assertEquals(
                "inherit",
                stateSettings(base)["rules"]?.jsonObject?.get("42")
                    ?.jsonObject?.get("replyInterruptionMode")?.jsonPrimitive?.content,
            )
            ReplyInterruptionMode.entries.stream().forEach { mode ->
                val value = mode.name.lowercase(Locale.ROOT)
                val response = request(
                    base,
                    "/api/chats/42/rule",
                    "PUT",
                    """{"enabled":false,"replyInterruptionMode":"$value"}""",
                )
                assertEquals(200, response.statusCode(), response.body())
                assertEquals(mode, settings.rule("42").replyInterruptionMode)
                assertEquals(mode, SettingsStore(directory).rule("42").replyInterruptionMode)
                val wire = stateSettings(base)
                assertEquals("finish", wire["replyProcessingMode"]?.jsonPrimitive?.content)
                assertEquals(
                    value,
                    wire["rules"]?.jsonObject?.get("42")
                        ?.jsonObject?.get("replyInterruptionMode")?.jsonPrimitive?.content,
                )
                assertFalse(settings.rule("42").enabled)
                assertTrue(settings.get().paused)
            }
            assertTrue(gateway.sent.isEmpty())
        }

    @Test
    fun `invalid per chat policy never enables automation or saves other rule changes`() =
        withServer { settings, gateway, base ->
            assertEquals(
                200,
                request(
                    base,
                    "/api/chats/42/rule",
                    "PUT",
                    """{"enabled":false,"replyInterruptionMode":"finish","instructions":"unchanged"}""",
                ).statusCode(),
            )
            val before = settings.get()
            Stream.of("null", "false", "7", "\"unknown\"", "\"INHERIT\"").forEach { value ->
                val response = request(
                    base,
                    "/api/chats/42/rule",
                    "PUT",
                    """{"enabled":true,"replyInterruptionMode":$value,"instructions":"must not persist"}""",
                )
                assertEquals(400, response.statusCode())
                assertEquals(before, settings.get())
                assertFalse(settings.rule("42").enabled)
            }
            assertEquals(
                "finish",
                stateSettings(base)["rules"]?.jsonObject?.get("42")
                    ?.jsonObject?.get("replyInterruptionMode")?.jsonPrimitive?.content,
            )
            assertTrue(gateway.sent.isEmpty())
        }

    private fun stateSettings(base: String): JsonObject {
        val response = request(base, "/api/state", "GET")
        assertEquals(200, response.statusCode())
        return Json.parseToJsonElement(response.body()).jsonObject.getValue("settings").jsonObject
    }

    private fun request(base: String, path: String, method: String, body: String? = null): HttpResponse<String> =
        HttpClient.newHttpClient().use { http ->
            http.send(
                HttpRequest.newBuilder(URI.create(base + path)).header("Cookie", "tiho=reply-policy")
                    .method(
                        method,
                        if (body == null) {
                            HttpRequest.BodyPublishers.noBody()
                        } else {
                            HttpRequest.BodyPublishers.ofString(body)
                        },
                    ).build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        }

    private fun withServer(action: (SettingsStore, FakeTelegramGateway, String) -> Unit) {
        val settings = SettingsStore(directory)
        val gateway = FakeTelegramGateway()
        val ai = AiTestServices("http://127.0.0.1:1")
        EngineManager(directory, ai.runtime).use { engine ->
            ReplyCoordinator(settings, gateway) { _, _ -> ReplyResult("Unused reply", 0, 0.0) }.use { replies ->
                TestWebServerFactory().create(directory, settings, gateway, engine, ai, replies, "reply-policy") {}
                    .use { server -> action(settings, gateway, server.start().substringBefore("/?")) }
            }
        }
    }
}
