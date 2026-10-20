package ru.boltunya.ai

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import ru.boltunya.domain.ChatRule
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class R1RequestPolicyTest {
    @Test
    fun `SDK requests and structured generation honor R1 temperature limits without changing native roles`() {
        val requests = ConcurrentLinkedQueue<JsonObject>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/chat") { exchange ->
            requests.add(Json.parseToJsonElement(String(exchange.requestBody.readAllBytes())).jsonObject)
            val bytes = """{"message":{"role":"assistant","content":"Готово"},"done_reason":"stop"}""".toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val services = AiTestServices("http://127.0.0.1:${server.address.port}")
            val messages = listOf(SystemMessage("Деловой стиль"), UserMessage("Привет"))
            listOf(0, 20, 60, 100).stream().forEach { creativity ->
                services.completion.generate(ChatRule(model = "deepseek-r1:8b", creativity = creativity), messages)
            }
            services.completion.generate(
                ChatRule(model = "deepseek-r1:8b"),
                messages,
                schema = JsonObject(emptyMap()),
            )
            val temperatures = requests.map {
                it.getValue("options").jsonObject.getValue("temperature").jsonPrimitive.content.toDouble()
            }
            assertEquals(listOf(0.5, 0.5, 0.6, 0.7, 0.5), temperatures)
            requests.stream().forEach { request ->
                val options = request.getValue("options").jsonObject
                assertEquals("true", request.getValue("think").jsonPrimitive.content)
                assertEquals("2176", options.getValue("num_predict").jsonPrimitive.content)
                assertTrue(listOf("top_p", "top_k", "repeat_penalty").none { it in options })
                val sent = request.getValue("messages").jsonArray
                assertEquals(
                    listOf("system", "user"),
                    sent.map {
                        it.jsonObject.getValue("role").jsonPrimitive.content
                    },
                )
                assertEquals("Деловой стиль", sent.first().jsonObject.getValue("content").jsonPrimitive.content)
                assertEquals("Привет", sent.last().jsonObject.getValue("content").jsonPrimitive.content)
            }
        } finally {
            server.stop(0)
        }
    }
}
