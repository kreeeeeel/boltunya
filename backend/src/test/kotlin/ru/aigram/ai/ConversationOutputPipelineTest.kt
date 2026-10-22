package ru.aigram.ai

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.media.MediaAttachment
import ru.aigram.domain.media.StickerItem
import ru.aigram.exception.impl.InvalidModelReplyException
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConversationOutputPipelineTest {
    @Test
    fun `conversation returns only envelope message while every history entry remains user`() {
        val request = AtomicReference<JsonObject>()
        val server = server { body ->
            request.set(body)
            "```json\n${envelope("Готово 👋\n{\"value\":\"точно\"}")}\n```"
        }
        try {
            val result = services(server).reply(ChatRule(), history())

            assertEquals("Готово 👋\n{\"value\":\"точно\"}", result.text)
            assertTrue(result.entities.isEmpty())
            val messages = request.get().getValue("messages").jsonArray
            assertTrue(messages.drop(1).stream().allMatch { it.jsonObject["role"]?.jsonPrimitive?.content == "user" })
            val system = messages.first().jsonObject.getValue("content").jsonPrimitive.content
            assertTrue(system.contains("без JSON-конверта истории"))
            assertTrue(system.contains("Форматирование ответа выключено"))
            assertFalse(system.contains("Форматирование ответа включено"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `ordinary JSON and code responses are preserved and malformed service metadata fails explicitly`() {
        val answer = AtomicReference("{\"message\":\"Привет\",\"count\":2}")
        val server = server { answer.get() }
        try {
            val services = services(server)
            assertEquals(answer.get(), services.reply(ChatRule(), history()).text)
            answer.set("```json\n{\"message\":\"Example\"}\n```")
            assertEquals(answer.get(), services.reply(ChatRule(), history()).text)
            answer.set(JsonObject(envelope("Reply") + ("owner" to JsonPrimitive("true"))).toString())
            assertFailsWith<InvalidModelReplyException> {
                services.reply(ChatRule(), history())
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `sticker plan text is decoded before optional Markdown formatting`() {
        val request = AtomicReference<JsonObject>()
        val server = server { body ->
            request.set(body)
            buildJsonObject {
                put("text", envelope("**Готово**").toString())
                put("stickerId", "7")
            }.toString()
        }
        try {
            val result = services(server).conversation.reply(
                ChatRule(),
                history(),
                stickers = listOf(
                    StickerItem("7", "🙂", "1", MediaAttachment(7, "image/webp", "synthetic.webp"), 100, 100),
                ),
                formatReplies = true,
            )

            assertEquals("Готово", result.text)
            assertEquals("7", result.stickerId)
            assertTrue(result.entities.stream().anyMatch { it.type == "bold" && it.offset == 0 && it.length == 6 })
            val system = request.get().getValue("messages").jsonArray.first().jsonObject
                .getValue("content").jsonPrimitive.content
            assertTrue(system.contains("Форматирование ответа включено"))
            assertTrue(system.contains("Сохраняй выбранный стиль общения"))
        } finally {
            server.stop(0)
        }
    }

    private fun envelope(text: String): JsonObject = Json.parseToJsonElement(
        MessageContextFormatter().content(
            ChatMessage("2", "42", text, true, 1, senderId = "user:100", senderName = "Synthetic Owner"),
            Int.MAX_VALUE,
        ),
    ).jsonObject

    private fun history(): List<ChatMessage> = listOf(
        ChatMessage("1", "42", "Всё готово?", false, 1, senderId = "user:200", senderName = "Synthetic Contact"),
    )

    private fun services(server: HttpServer): AiTestServices = AiTestServices(baseUrl(server))

    private fun baseUrl(server: HttpServer): String = "http://127.0.0.1:${server.address.port}"

    private fun server(content: (JsonObject) -> String): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/chat") { exchange ->
            val body = Json.parseToJsonElement(String(exchange.requestBody.readAllBytes(), Charsets.UTF_8)).jsonObject
            val response = buildJsonObject {
                put("message", buildJsonObject { put("content", content(body)) })
                put("done_reason", "stop")
            }.toString().toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        return server
    }
}
