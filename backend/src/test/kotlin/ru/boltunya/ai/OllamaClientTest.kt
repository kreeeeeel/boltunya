package ru.boltunya.ai

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.exception.IntegrationException
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OllamaClientTest {
    @Test
    fun `sends bounded history with thinking disabled and measures response speed`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var requestBody = ""
        server.createContext("/api/chat") { exchange ->
            requestBody = String(exchange.requestBody.readAllBytes(), Charsets.UTF_8)
            val bytes = """{"message":{"content":"Привет!"},"eval_count":10,"eval_duration":100000000}"""
                .toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val client = OllamaClient("http://127.0.0.1:${server.address.port}")
            val result = client.reply(
                ChatRule(internetAccess = false),
                listOf(ChatMessage("1", "42", "Как дела?", false, 0)),
            )
            val body = Json.parseToJsonElement(requestBody).jsonObject
            assertEquals("false", body["think"]?.jsonPrimitive?.content)
            assertEquals("20m", body["keep_alive"]?.jsonPrimitive?.content)
            assertEquals(
                "Как дела?",
                body["messages"]?.jsonArray?.last()?.jsonObject?.get("content")?.jsonPrimitive?.content,
            )
            assertEquals("Привет!", result.text)
            assertEquals(100.0, result.tokensPerSecond)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `retries assistant boilerplate once and returns the revised answer`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val calls = AtomicInteger()
        var lastRequest = ""
        server.createContext("/api/chat") { exchange ->
            lastRequest = String(exchange.requestBody.readAllBytes(), Charsets.UTF_8)
            val text = if (calls.incrementAndGet() ==
                1
            ) {
                "Я ИИ-помощник, у меня нет личных отношений"
            } else {
                "Ну ты, конечно)"
            }
            val bytes = "{\"message\":{\"content\":\"$text\"}}".toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val client = OllamaClient("http://127.0.0.1:${server.address.port}")
            val result = client.reply(
                ChatRule(internetAccess = false),
                listOf(ChatMessage("1", "42", "Кто лучшая сестра?", false, 0)),
            )
            assertEquals("Ну ты, конечно)", result.text)
            assertEquals(2, calls.get())
            val messages = Json.parseToJsonElement(lastRequest).jsonObject["messages"]?.jsonArray
            assertEquals("user", messages?.last()?.jsonObject?.get("role")?.jsonPrimitive?.content)
            assertTrue(messages?.first().toString().contains("Предыдущий вариант"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `instruct models omit thinking and unsafe repeated replies are not returned`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val calls = AtomicInteger()
        server.createContext("/api/chat") { exchange ->
            val body = Json.parseToJsonElement(String(exchange.requestBody.readAllBytes())).jsonObject
            assertFalse(body.containsKey("think"))
            calls.incrementAndGet()
            val bytes = """{"message":{"content":"Я ИИ-помощник, у меня нет личных отношений"}}""".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val client = OllamaClient("http://127.0.0.1:${server.address.port}")
            assertFailsWith<IntegrationException> {
                client.reply(
                    ChatRule(model = "qwen2.5:7b", internetAccess = false),
                    listOf(ChatMessage("1", "42", "Привет", false, 0)),
                )
            }
            assertEquals(2, calls.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `unavailable engine produces an explicit integration error`() {
        val client = OllamaClient("http://127.0.0.1:1")
        assertFailsWith<IntegrationException> { client.installed() }
    }
}
