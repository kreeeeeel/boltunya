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
import ru.boltunya.exception.impl.EmptyModelReplyException
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConversationServiceTest {
    @Test
    fun `reasoning budget retries a truncated DeepSeek response without returning private thoughts`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val budgets = mutableListOf<Int>()
        server.createContext("/api/chat") { exchange ->
            val body = Json.parseToJsonElement(String(exchange.requestBody.readAllBytes())).jsonObject
            budgets.add(body.getValue("options").jsonObject.getValue("num_predict").jsonPrimitive.content.toInt())
            val response = if (budgets.size == 1) {
                """{
                  "message":{"content":"","thinking":"private reasoning"},
                  "done_reason":"length","prompt_eval_count":10,"eval_count":128
                }"""
            } else {
                """{"message":{"content":"<think>private reasoning</think>Нормально, а ты?"},"eval_count":10,"eval_duration":100000000}"""
            }
            val bytes = response.toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val result = AiTestServices("http://127.0.0.1:${server.address.port}").reply(
                ChatRule(model = "deepseek-r1:8b", maxTokens = 128, internetAccess = false),
                listOf(ChatMessage("1", "test", "Как дела?", false, 0)),
            )
            assertEquals(listOf(2176, 4224), budgets)
            assertEquals("Нормально, а ты?", result.text)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `repeated reasoning without an answer fails explicitly after one bounded retry`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val calls = AtomicInteger()
        server.createContext("/api/chat") { exchange ->
            exchange.requestBody.readAllBytes()
            calls.incrementAndGet()
            val bytes = """{
              "message":{"content":"","thinking":"private reasoning"},
              "done_reason":"length","prompt_eval_count":10,"eval_count":128
            }"""
                .toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            assertFailsWith<EmptyModelReplyException> {
                AiTestServices("http://127.0.0.1:${server.address.port}").reply(
                    ChatRule(model = "deepseek-r1:8b", internetAccess = false),
                    listOf(ChatMessage("1", "test", "Как дела?", false, 0)),
                )
            }
            assertEquals(2, calls.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `sends bounded history with thinking disabled and measures response speed`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var requestBody = ""
        server.createContext("/api/chat") { exchange ->
            requestBody = String(exchange.requestBody.readAllBytes(), Charsets.UTF_8)
            val bytes = """{"message":{"content":"Привет!"},"eval_count":10,"eval_duration":100000000}"""
                .toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val client = AiTestServices("http://127.0.0.1:${server.address.port}")
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
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val client = AiTestServices("http://127.0.0.1:${server.address.port}")
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
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val client = AiTestServices("http://127.0.0.1:${server.address.port}")
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
        val client = AiTestServices("http://127.0.0.1:1")
        assertFailsWith<IntegrationException> { client.installed() }
    }
}
