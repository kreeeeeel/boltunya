package ru.aigram.controller.integration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.AiTestServices
import ru.aigram.ai.EngineManager
import ru.aigram.automation.FakeTelegramGateway
import ru.aigram.automation.MemoryQueueTask
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.automation.proactive.ProactiveTask
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.chat.ProactiveMessageMode
import ru.aigram.domain.chat.ProactiveMode
import ru.aigram.storage.SettingsStore
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProactiveRuleEndpointTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `legacy rule JSON keeps AI proactive defaults when new optional fields are absent`() = withServer {
            settings,
            _,
            _,
            base,
        ->
        val response = put(base, """{"enabled":false,"style":"concise"}""")
        assertEquals(200, response.statusCode())
        val rule = SettingsStore(directory).rule("42")
        assertEquals(ProactiveMessageMode.AI, rule.proactiveMessageMode)
        assertEquals(ProactiveMode.INHERIT, rule.proactiveMode)
        assertEquals("", rule.proactiveText)
        assertEquals(0, rule.proactiveIntervalMinutes)
        assertEquals("concise", rule.style)
        assertFalse(settings.rule("42").enabled)
    }

    @Test
    fun `fixed proactive text can be enabled and persisted with replies disabled and an unavailable model`() =
        withServer {
                settings,
                gateway,
                _,
                base,
            ->
            val text = "  **Literal** 😀\nСпросить о встрече  "
            val response = put(
                base,
                """
                {
                    "enabled":false,
                    "proactiveMode":"enabled",
                    "proactiveMessageMode":"text",
                    "proactiveText":${JsonPrimitive(text)},
                    "useDefaultModel":false,
                    "model":"not-installed-in-tests",
                    "proactiveIntervalMinutes":90,
                    "useMemory":false
                }
                """.trimIndent(),
            )
            assertEquals(200, response.statusCode(), response.body())
            val rule = SettingsStore(directory).rule("42")
            assertEquals(ProactiveMessageMode.TEXT, rule.proactiveMessageMode)
            assertEquals(ProactiveMode.ENABLED, rule.proactiveMode)
            assertEquals(text, rule.proactiveText)
            assertEquals(90, rule.proactiveIntervalMinutes)
            assertEquals("not-installed-in-tests", rule.model)
            assertFalse(rule.enabled)
            assertFalse(rule.useMemory)
            assertEquals(rule, settings.rule("42"))
            assertTrue(gateway.sent.isEmpty())
        }

    @Test
    fun `validates text enum and length atomically and permits blank text only when disabled`() = withServer {
            settings,
            gateway,
            _,
            base,
        ->
        val before = settings.get()
        val blank = put(
            base,
            """
                {
                    "enabled":false,
                    "proactiveMode":"enabled",
                    "proactiveMessageMode":"text",
                    "proactiveText":${JsonPrimitive(" \t \n ")}
                }
            """.trimIndent(),
        )
        assertEquals(400, blank.statusCode())
        assertEquals(
            "PROACTIVE_TEXT_REQUIRED",
            Json.parseToJsonElement(blank.body()).jsonObject["code"]?.jsonPrimitive?.content,
        )
        assertEquals(before, settings.get())
        Stream.of(
            """{"proactiveMessageMode":"other"}""",
            """{"proactiveMessageMode":null}""",
            """{"proactiveMode":"other"}""",
            """{"proactiveMode":"enabled","proactiveMessageMode":"text","proactiveText":"${"x".repeat(4097)}"}""",
        ).forEach { body ->
            assertEquals(400, put(base, body).statusCode())
            assertEquals(before, settings.get())
        }
        assertEquals(
            200,
            put(
                base,
                """
                    {
                        "enabled":false,
                        "proactiveMode":"enabled",
                        "proactiveMessageMode":"text",
                        "proactiveText":"${"x".repeat(4096)}"
                    }
                """.trimIndent(),
            ).statusCode(),
        )
        assertEquals(4096, settings.rule("42").proactiveText.length)
        assertEquals(
            200,
            put(
                base,
                """{"enabled":false,"proactiveMode":"disabled","proactiveMessageMode":"text","proactiveText":""}""",
            ).statusCode(),
        )
        assertEquals("", settings.rule("42").proactiveText)
        assertEquals(ProactiveMode.DISABLED, settings.rule("42").proactiveMode)
        assertTrue(gateway.sent.isEmpty())
    }

    @Test
    fun `saving a changed proactive rule cancels its queued initiative but keeps queued memory`() = withServer {
            settings,
            gateway,
            replies,
            base,
        ->
        settings.update { it.copy(paused = false, replyDelaySeconds = 120, showTyping = false) }
        settings.saveRule(
            "42",
            ChatRule(
                enabled = false,
                proactiveMode = ProactiveMode.ENABLED,
                proactiveMessageMode = ProactiveMessageMode.TEXT,
                proactiveText = "До изменения",
            ),
        )
        val generated = AtomicInteger()
        assertTrue(
            replies.enqueueInitiative(
                gateway.chats().first().copy(lastMessageId = "1"),
                "100",
                ProactiveTask(
                    allowed = { true },
                    generate = { _, _ ->
                        generated.incrementAndGet()
                        ReplyResult("Не отправлять", 0, 0.0)
                    },
                    prepareDelivery = { true },
                    trigger = "Synthetic proactive task",
                ),
            ),
        )
        replies.pauseQueue(true)
        replies.enqueueMemory(
            MemoryQueueTask(
                "memory", "42", "100", "Chat", "", "gemma3:4b", true,
                { true }, { throw AssertionError("Memory must remain queued") }, {}, {},
            ),
        )
        assertEquals(
            200,
            put(
                base,
                """
                {
                    "enabled":false,
                    "proactiveMode":"enabled",
                    "proactiveMessageMode":"text",
                    "proactiveText":"После изменения"
                }
                """.trimIndent(),
            ).statusCode(),
        )
        assertEquals("cancelled", replies.activities().single { it.kind.name == "INITIATIVE" }.status)
        assertEquals("queued", replies.activities().single { it.kind.name == "MEMORY" }.status)
        assertEquals(1, replies.queue().queued)
        assertEquals(0, generated.get())
        assertTrue(gateway.sent.isEmpty())
    }

    private fun put(base: String, body: String): HttpResponse<String> = HttpClient.newHttpClient().use { http ->
        http.send(
            HttpRequest.newBuilder(URI.create("$base/api/chats/42/rule"))
                .header("Cookie", "tiho=proactive-rules")
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

    private fun withServer(action: (SettingsStore, FakeTelegramGateway, ReplyCoordinator, String) -> Unit) {
        val settings = SettingsStore(directory)
        val gateway = FakeTelegramGateway()
        val services = AiTestServices("http://127.0.0.1:1")
        EngineManager(directory, services.runtime).use { engine ->
            ReplyCoordinator(settings, gateway) { _, _ ->
                ReplyResult("Unused automatic reply", 0, 0.0)
            }.use { replies ->
                TestWebServerFactory().create(
                    directory,
                    settings,
                    gateway,
                    engine,
                    services,
                    replies,
                    "proactive-rules",
                ) {
                }
                    .use { server -> action(settings, gateway, replies, server.start().substringBefore("/?")) }
            }
        }
    }
}
