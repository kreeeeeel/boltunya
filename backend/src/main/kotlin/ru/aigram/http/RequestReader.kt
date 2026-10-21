package ru.aigram.http

import com.sun.net.httpserver.HttpExchange
import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component
import ru.aigram.exception.impl.InvalidFieldLengthException
import ru.aigram.exception.impl.MalformedPayloadException
import ru.aigram.exception.impl.RequestTooLargeException

private const val MAX_REQUEST_BODY_BYTES = 512 * 1024

@Component
class RequestReader @Inject constructor(private val json: Json) {
    fun read(exchange: HttpExchange): JsonObject {
        val bytes = exchange.requestBody.readNBytes(MAX_REQUEST_BODY_BYTES + 1)
        if (bytes.size > MAX_REQUEST_BODY_BYTES) throw RequestTooLargeException()
        return if (bytes.isEmpty()) {
            JsonObject(emptyMap())
        } else {
            json.parseToJsonElement(String(bytes, Charsets.UTF_8)) as? JsonObject
                ?: throw MalformedPayloadException()
        }
    }

    fun text(body: JsonObject, key: String, maximum: Int): String {
        val primitive = body[key] as? JsonPrimitive ?: throw InvalidFieldLengthException(key, maximum)
        if (!primitive.isString) throw InvalidFieldLengthException(key, maximum)
        val text = primitive.content.trim()
        if (text.isBlank() || text.length > maximum) throw InvalidFieldLengthException(key, maximum)
        return text
    }
}
