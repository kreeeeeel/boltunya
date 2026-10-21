package ru.aigram.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.stereotype.Component
import ru.aigram.domain.ai.ModelReplyPlan
import ru.aigram.domain.media.StickerItem
import ru.aigram.exception.impl.InvalidModelReplyException
import ru.aigram.resources.ResourceCatalog

@Component
class ReplyMediaPlanner(private val resources: ResourceCatalog) {
    fun schema(stickers: List<StickerItem>): JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        put("required", JsonArray(listOf(JsonPrimitive("text"), JsonPrimitive("stickerId"))))
        put(
            "properties",
            buildJsonObject {
                put("text", buildJsonObject { put("type", "string") })
                put(
                    "stickerId",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "enum",
                            JsonArray(
                                listOf(JsonPrimitive("")) + stickers.stream().map {
                                    JsonPrimitive(it.id)
                                }.toList(),
                            ),
                        )
                    },
                )
            },
        )
    }

    fun messages(context: List<Message>, stickers: List<StickerItem>): List<Message> {
        val choices = buildJsonArray {
            stickers.stream().forEach { sticker ->
                add(
                    buildJsonObject {
                        put("id", sticker.id)
                        put("emoji", sticker.emoji)
                    },
                )
            }
        }
        val instruction = resources.prompt("ru/conversation/sticker-plan", mapOf("stickers" to choices.toString()))
        return context.stream().map { message ->
            if (message is SystemMessage) {
                SystemMessage(message.text.orEmpty() + "\n" + instruction)
            } else {
                message
            }
        }.toList()
    }

    fun read(text: String, stickers: List<StickerItem>): ModelReplyPlan {
        val parsed = try {
            Json.parseToJsonElement(text) as? JsonObject
        } catch (_: Exception) {
            null
        }
        val answer = parsed?.get("text") as? JsonPrimitive
        val sticker = parsed?.get("stickerId") as? JsonPrimitive
        if (parsed == null || answer == null || !answer.isString || sticker == null || !sticker.isString ||
            parsed.keys != setOf("text", "stickerId") || answer.content.length > 4096 ||
            sticker.content.isNotEmpty() && stickers.stream().noneMatch { it.id == sticker.content }
        ) {
            throw InvalidModelReplyException()
        }
        return ModelReplyPlan(answer.content.trim(), sticker.content)
    }
}
