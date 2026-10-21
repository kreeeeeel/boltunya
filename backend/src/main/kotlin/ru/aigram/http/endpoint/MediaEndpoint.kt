package ru.aigram.http.endpoint

import com.sun.net.httpserver.HttpExchange
import jakarta.inject.Inject
import org.springframework.stereotype.Component
import ru.aigram.exception.impl.InvalidMediaIdentifierException
import ru.aigram.http.MediaResponder
import ru.aigram.telegram.TelegramGateway

@Component
class MediaEndpoint @Inject constructor(private val telegram: TelegramGateway, private val responder: MediaResponder) {
    fun download(id: String) {
        telegram.media(id.toIntOrNull() ?: throw InvalidMediaIdentifierException())
    }
    fun get(exchange: HttpExchange, id: String) {
        val fileId = id.toIntOrNull() ?: throw InvalidMediaIdentifierException()
        responder.respond(exchange, telegram.media(fileId))
    }
}
