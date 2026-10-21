package ru.aigram.http

import com.sun.net.httpserver.HttpExchange
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonElement
import org.springframework.stereotype.Component

private const val HTTP_OK = 200

@Component
class ResponseWriter @Inject constructor() {
    fun json(exchange: HttpExchange, value: JsonElement, status: Int = HTTP_OK) {
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        bytes(exchange, status, value.toString().toByteArray(Charsets.UTF_8))
    }

    fun bytes(exchange: HttpExchange, status: Int, bytes: ByteArray) {
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.write(bytes)
    }
}
