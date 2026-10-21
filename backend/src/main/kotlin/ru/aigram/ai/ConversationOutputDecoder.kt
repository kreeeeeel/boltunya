package ru.aigram.ai

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import org.springframework.stereotype.Component
import ru.aigram.exception.impl.InvalidModelReplyException
import java.util.Locale

private val ENVELOPE_STRING_FIELDS = setOf("username", "name", "message", "timestamp", "senderId", "messageId")
private val ENVELOPE_BOOLEAN_FIELDS = setOf("owner", "automated")
private val ENVELOPE_OPTIONAL_FIELDS = setOf("replyToId", "replyText")
private val ENVELOPE_REQUIRED_FIELDS = ENVELOPE_STRING_FIELDS + ENVELOPE_BOOLEAN_FIELDS
private val ENVELOPE_ALLOWED_FIELDS = ENVELOPE_REQUIRED_FIELDS + ENVELOPE_OPTIONAL_FIELDS
private val ENVELOPE_CONTENT_FIELDS = setOf("username", "name", "message", "timestamp")

/** Decodes only the conversation history envelope, never arbitrary model JSON. */
@Component
class ConversationOutputDecoder {
    fun decode(text: String): String {
        val source = jsonSource(text) ?: return text
        val parsed = try {
            Json.parseToJsonElement(source) as? JsonObject
        } catch (_: SerializationException) {
            return text
        }
        if (parsed == null || !recognizable(parsed)) {
            return text
        }

        val stringsValid = ENVELOPE_STRING_FIELDS.stream().allMatch { string(parsed[it]) }
        val booleansValid = ENVELOPE_BOOLEAN_FIELDS.stream().allMatch { key ->
            val value = parsed[key] as? JsonPrimitive
            value != null && !value.isString && value.booleanOrNull != null
        }
        val optionalValid = ENVELOPE_OPTIONAL_FIELDS.stream().allMatch { key ->
            !parsed.containsKey(key) || string(parsed[key])
        }
        if (!parsed.keys.containsAll(ENVELOPE_REQUIRED_FIELDS) ||
            !ENVELOPE_ALLOWED_FIELDS.containsAll(parsed.keys) || !stringsValid || !booleansValid || !optionalValid
        ) {
            throw InvalidModelReplyException()
        }
        return (parsed.getValue("message") as JsonPrimitive).content
    }

    private fun recognizable(value: JsonObject): Boolean {
        val contentWithMetadata = value.keys.containsAll(ENVELOPE_CONTENT_FIELDS) &&
            (
                value.containsKey("owner") || value.containsKey("automated") ||
                    value.containsKey("senderId") && value.containsKey("messageId")
                )
        val completeAuthorMetadata = value.keys.containsAll(ENVELOPE_BOOLEAN_FIELDS) && value.containsKey("senderId") &&
            value.containsKey("messageId") && value.containsKey("message")
        return contentWithMetadata || completeAuthorMetadata
    }

    private fun string(value: JsonElement?): Boolean = value is JsonPrimitive && value.isString

    private fun jsonSource(text: String): String? {
        val source = text.trim()
        if (!source.startsWith("```")) {
            return source
        }
        val opening = source.indexOf('\n')
        if (opening < 0 || !source.endsWith("```")) {
            return null
        }
        val language = source.substring(3, opening).trim().lowercase(Locale.ROOT)
        if (language.isNotEmpty() && language != "json") {
            return null
        }
        val closing = source.length - 3
        if (closing <= opening || source[closing - 1] != '\n') {
            return null
        }
        return source.substring(opening + 1, closing).trim()
    }
}
