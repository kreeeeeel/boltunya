package ru.boltunya.http.endpoint

import com.sun.net.httpserver.HttpExchange
import jakarta.inject.Inject
import ru.boltunya.exception.impl.InvalidMediaIdentifierException
import ru.boltunya.http.MediaResponder
import ru.boltunya.telegram.TelegramGateway

class MediaEndpoint @Inject constructor(private val telegram: TelegramGateway, private val responder: MediaResponder) {
    fun download(id: String) {
        telegram.media(id.toIntOrNull() ?: throw InvalidMediaIdentifierException())
    }
    fun get(exchange: HttpExchange, id: String) {
        val fileId = id.toIntOrNull() ?: throw InvalidMediaIdentifierException()
        responder.respond(exchange, telegram.media(fileId))
    }
}
