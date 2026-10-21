package ru.aigram.ai

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import ru.aigram.ai.research.InternetResearcher
import ru.aigram.ai.research.SearchProvider
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatRule
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OllamaInternetTest {
    @Test
    fun `unavailable sources cannot produce invented facts or a clock answer`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val calls = AtomicInteger()
        server.createContext("/api/chat") { exchange ->
            calls.incrementAndGet()
            val response = buildJsonObject {
                put("message", buildJsonObject { put("content", """{"kind":"search","query":"Kotlin release"}""") })
            }.toString().toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val client =
                AiTestServices(
                    "http://127.0.0.1:${server.address.port}",
                    researcher = InternetResearcher(
                        search = SearchProvider {
                            emptyList()
                        },
                    ),
                )
            val answer = client.reply(
                ChatRule(),
                listOf(ChatMessage("1", "test", "Найди последнюю версию Kotlin", false, 0)),
            )
            assertEquals("Не получилось надёжно проверить это сейчас.", answer.text)
            assertEquals(1, calls.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `weather without a city injects clarification and disabled access skips planning`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val plans = AtomicInteger()
        var generatedSystem = ""
        server.createContext("/api/chat") { exchange ->
            val request = Json.parseToJsonElement(String(exchange.requestBody.readAllBytes())).jsonObject
            val content = if (request["format"] != null) {
                plans.incrementAndGet()
                """{"kind":"weather","query":"","location":"","dayOffset":0}"""
            } else {
                generatedSystem =
                    request["messages"]?.jsonArray?.first()?.jsonObject?.get(
                        "content",
                    )?.jsonPrimitive?.content.orEmpty()
                "В каком городе?"
            }
            val bytes = buildJsonObject {
                put("message", buildJsonObject { put("content", content) })
            }.toString().toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val client = AiTestServices("http://127.0.0.1:${server.address.port}")
            val history = listOf(ChatMessage("1", "1", "Как погода?", false, 0))
            assertEquals("В каком городе?", client.reply(ChatRule(), history).text)
            assertTrue(generatedSystem.contains("Не угадывай местоположение"))
            assertTrue(generatedSystem.contains("НЕДОВЕРЕННЫЕ"))
            client.reply(ChatRule(internetAccess = false), history)
            assertEquals(1, plans.get())
        } finally {
            server.stop(0)
        }
    }
}
