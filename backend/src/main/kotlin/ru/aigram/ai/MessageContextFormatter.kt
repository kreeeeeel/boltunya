package ru.aigram.ai

import jakarta.inject.Inject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.springframework.stereotype.Component
import ru.aigram.domain.ChatMessage
import java.time.Instant

private const val MAX_AUTHOR_CHARACTERS = 128
private const val MAX_QUOTE_CHARACTERS = 600

@Component
class MessageContextFormatter @Inject constructor() {
    fun content(message: ChatMessage, textLimit: Int): String = buildJsonObject {
        val username = message.senderUsername.removePrefix("@").take(MAX_AUTHOR_CHARACTERS)
        put("username", if (username.isBlank()) "" else "@$username")
        put("name", message.senderName.take(MAX_AUTHOR_CHARACTERS))
        put("message", message.text.take(textLimit))
        put("timestamp", if (message.date > 0) Instant.ofEpochSecond(message.date).toString() else "")
        put("senderId", message.senderId)
        put("owner", message.outgoing)
        put("automated", message.automated)
        put("messageId", message.id)
        if (message.replyToId.isNotBlank()) put("replyToId", message.replyToId)
        if (message.replyText.isNotBlank()) put("replyText", message.replyText.take(MAX_QUOTE_CHARACTERS))
    }.toString()

    fun data(message: ChatMessage, textLimit: Int): JsonObject = buildJsonObject {
        put("messageId", message.id)
        put("sender", if (message.outgoing) "owner" else "contact")
        put("senderId", message.senderId)
        put("senderName", message.senderName.take(MAX_AUTHOR_CHARACTERS))
        put("automated", message.automated)
        put("group", message.group)
        put("text", message.text.take(textLimit))
        if (message.replyToId.isNotBlank()) put("replyToId", message.replyToId)
        if (message.replyText.isNotBlank()) put("replyText", message.replyText.take(MAX_QUOTE_CHARACTERS))
    }
}
