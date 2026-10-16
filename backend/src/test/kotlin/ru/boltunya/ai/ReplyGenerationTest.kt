package ru.boltunya.ai

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ReplyGenerationTest {
    @Test
    fun `generates conversational reply directly without classification or fixed refusal`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val calls = AtomicInteger()
        server.createContext("/api/chat") { exchange ->
            val body = Json.parseToJsonElement(String(exchange.requestBody.readAllBytes())).jsonObject
            assertFalse(body.containsKey("format"))
            val messages = body["messages"]?.jsonArray.orEmpty()
            assertEquals("Привет! Как дела?", messages.last().jsonObject["content"]?.jsonPrimitive?.content)
            calls.incrementAndGet()
            val bytes = """{"message":{"content":"Привет! Всё хорошо, а у тебя?"}}""".toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val client = AiTestServices("http://127.0.0.1:${server.address.port}")
            val reply = client.reply(
                ChatRule(internetAccess = false),
                listOf(ChatMessage("1", "42", "Привет! Как дела?", false, 0)),
            )
            assertEquals("Привет! Всё хорошо, а у тебя?", reply.text)
            assertEquals(1, calls.get())
        } finally {
            server.stop(0)
        }
    }
}
