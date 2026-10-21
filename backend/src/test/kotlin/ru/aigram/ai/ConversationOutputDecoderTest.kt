package ru.aigram.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.exception.impl.InvalidModelReplyException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConversationOutputDecoderTest {
    private val decoder = ConversationOutputDecoder()

    @Test
    fun `decodes only the agreed history envelope and retains nested strings whitespace and Unicode`() {
        val message = "  Ответ 👋\n{\"message\":\"это содержимое\",\"quote\":\"a\\b\"}  "
        val envelope = envelope(message)

        assertEquals(message, decoder.decode(envelope.toString()))
        assertEquals(message, decoder.decode("  ```json\r\n$envelope\r\n```  "))
        assertEquals(message, decoder.decode("```\n$envelope\n```"))
        assertEquals(message, decoder.decode("```JSON\n$envelope\n```"))
        val escaped = envelope.toString().replace("👋", "\\ud83d\\udc4b")
        assertEquals(message, decoder.decode(escaped))
        assertEquals("", decoder.decode(envelope("").toString()))
    }

    @Test
    fun `does not decode user JSON code or JSON stored inside a confirmed message field twice`() {
        val inputs = listOf(
            "{\"message\":\"Привет\",\"status\":\"ok\"}",
            "{\"username\":\"test\",\"name\":\"Test\",\"message\":\"Value\",\"timestamp\":\"now\"}",
            "[${envelope("Reply")}]",
            "```json\n{\"message\":\"Example\"}\n```",
            "```kotlin\nval message = \"hello\"\n```",
            "Before\n```json\n${envelope("Reply")}\n```",
            "```json\n${envelope("Reply")}\n```\nAfter",
            "  ordinary text with **symbols** and {braces}  ",
            "{\"message\":",
        )
        inputs.stream().forEach { input -> assertEquals(input, decoder.decode(input)) }
        val inner = envelope("Inner text").toString()
        assertEquals(inner, decoder.decode(envelope(inner).toString()))
    }

    @Test
    fun `rejects recognizable malformed service envelopes without leaking their metadata`() {
        val original = envelope("Reply")
        val invalid = listOf(
            JsonObject(original + ("owner" to JsonPrimitive("true"))),
            JsonObject(original + ("automated" to JsonPrimitive(1))),
            JsonObject(original + ("message" to JsonObject(emptyMap()))),
            JsonObject(original + ("message" to JsonNull)),
            JsonObject(original + ("replyText" to JsonNull)),
            JsonObject(original + ("unexpected" to JsonPrimitive("value"))),
            JsonObject(original - "timestamp"),
            JsonObject(original - "messageId"),
            JsonObject(original - "username"),
        )
        invalid.stream().forEach { input ->
            assertFailsWith<InvalidModelReplyException> { decoder.decode(input.toString()) }
            assertFailsWith<InvalidModelReplyException> { decoder.decode("```json\n$input\n```") }
        }
    }

    private fun envelope(text: String): JsonObject {
        val message = ChatMessage(
            "123", "456", text, true, 1,
            senderId = "user:789", senderName = "Synthetic Owner", senderUsername = "synthetic_owner",
            replyToId = "122", replyText = "Synthetic quoted text",
        )
        return Json.parseToJsonElement(MessageContextFormatter().content(message, Int.MAX_VALUE)) as JsonObject
    }
}
