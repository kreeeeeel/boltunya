package ru.aigram.http

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.AiTestServices
import ru.aigram.ai.EngineManager
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ChatMessage
import ru.aigram.domain.ReplyResult
import ru.aigram.storage.SettingsStore
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.IntStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DraftEndpointTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `queued selected message draft uses real bounded history once and never sends Telegram messages`() {
        val inference = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = AtomicInteger()
        val prompt = AtomicReference("")
        inference.createContext("/api/chat") { exchange ->
            exchange.use {
                requests.incrementAndGet()
                prompt.set(exchange.requestBody.bufferedReader().readText())
                val bytes =
                    """{"message":{"role":"assistant","content":"Draft answer"},"done":true,"done_reason":"stop"}"""
                        .toByteArray()
                exchange.responseHeaders.set("Content-Type", "application/x-ndjson")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.write(bytes)
            }
        }
        inference.start()
        try {
            val settings = SettingsStore(directory)
            settings.update { it.copy(memoryEnabled = false) }
            val gateway = MediaActionsGateway()
            gateway.delegate.messages = listOf(
                ChatMessage("1", "42", "Earlier incoming", false, 1),
                ChatMessage("2", "42", "Owner reply", true, 2),
                ChatMessage("3", "42", "Selected incoming", false, 3),
                ChatMessage("4", "42", "Newer incoming must not be included", false, 4),
            )
            val ai = AiTestServices("http://127.0.0.1:${inference.address.port}")
            EngineManager(directory, ai.runtime).use { engine ->
                ReplyCoordinator(settings, gateway, tracing = ai.tracing) { _, _ ->
                    ReplyResult("auto", 0, 0.0)
                }.use { queue ->
                    TestWebServerFactory().create(directory, settings, gateway, engine, ai, queue, "draft-session") {
                    }.use { server ->
                        val base = server.start().substringBefore("/?")
                        val http = HttpClient.newHttpClient()
                        fun request(path: String, method: String, body: String = "{}"): HttpResponse<String> =
                            http.send(
                                HttpRequest.newBuilder(URI.create(base + path)).header("Cookie", "tiho=draft-session")
                                    .method(method, HttpRequest.BodyPublishers.ofString(body)).build(),
                                HttpResponse.BodyHandlers.ofString(),
                            )
                        fun enqueue(): String {
                            val response =
                                request(
                                    "/api/preview/jobs",
                                    "POST",
                                    """{"chatId":"42","messageId":"3","text":"Fabricated preview must not be used","rule":{"internetAccess":false,"matchMyStyle":false,"useMemory":false}}""",
                                )
                            assertEquals(200, response.statusCode())
                            return Json.parseToJsonElement(
                                response.body(),
                            ).jsonObject["id"]?.jsonPrimitive?.content.orEmpty()
                        }
                        fun finished(id: String): JsonObject {
                            val final = AtomicReference<JsonObject>()
                            assertTrue(
                                IntStream.range(0, 300).anyMatch {
                                    val body = Json.parseToJsonElement(
                                        request("/api/preview/jobs/$id", "GET").body(),
                                    ).jsonObject
                                    if (body["status"]?.jsonPrimitive?.content in
                                        setOf("ready", "error", "cancelled")
                                    ) {
                                        final.set(body)
                                        true
                                    } else {
                                        TimeUnit.MILLISECONDS.sleep(10)
                                        false
                                    }
                                },
                            )
                            return final.get()
                        }
                        assertEquals("ready", finished(enqueue())["status"]?.jsonPrimitive?.content)
                        assertEquals(1, requests.get())
                        assertEquals(1, gateway.delegate.reads.size)
                        assertEquals(4L, gateway.delegate.reads.single().second)
                        assertTrue(prompt.get().contains("Selected incoming"))
                        assertFalse(prompt.get().contains("Newer incoming must not be included"))
                        assertFalse(prompt.get().contains("Fabricated preview must not be used"))
                        assertTrue(gateway.textSent.isEmpty())
                        assertEquals(
                            200,
                            request(
                                "/api/chats/42/messages/3/context",
                                "PUT",
                                """{"scope":"message","excluded":true}""",
                            ).statusCode(),
                        )
                        assertEquals("error", finished(enqueue())["status"]?.jsonPrimitive?.content)
                        assertEquals(1, requests.get())
                    }
                }
            }
        } finally {
            inference.stop(0)
        }
    }
}
