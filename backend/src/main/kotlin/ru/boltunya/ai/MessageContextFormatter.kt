package ru.boltunya.ai

import jakarta.inject.Inject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import ru.boltunya.domain.ChatMessage
import ru.boltunya.resources.ResourceCatalog

private const val MAX_AUTHOR_CHARACTERS = 128
private const val MAX_QUOTE_CHARACTERS = 600

class MessageContextFormatter @Inject constructor(private val resources: ResourceCatalog) {
    fun content(message: ChatMessage, textLimit: Int): String {
        val reply = if (message.replyToId.isNotBlank() || message.replyText.isNotBlank()) {
            resources.prompt(
                "ru/conversation/quoted-message",
                mapOf(
                    "id" to JsonPrimitive(message.replyToId).toString(),
                    "text" to JsonPrimitive(message.replyText.take(MAX_QUOTE_CHARACTERS)).toString(),
                ),
            )
        } else {
            ""
        }
        return resources.prompt(
            "ru/conversation/attributed-message",
            mapOf(
                "id" to JsonPrimitive(message.id).toString(),
                "name" to JsonPrimitive(
                    message.senderName.take(MAX_AUTHOR_CHARACTERS).ifBlank {
                        resources.prompt("ru/conversation/contact")
                    },
                ).toString(),
                "sender" to JsonPrimitive(message.senderId).toString(),
                "reply" to reply,
                "text" to message.text.take(textLimit),
            ),
        )
    }

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
