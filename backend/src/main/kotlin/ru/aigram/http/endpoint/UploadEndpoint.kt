package ru.aigram.http.endpoint

import com.sun.net.httpserver.HttpExchange
import jakarta.inject.Inject
import org.springframework.stereotype.Component
import ru.aigram.domain.UploadedFile
import ru.aigram.exception.impl.InvalidUploadException
import ru.aigram.exception.impl.TelegramNotConnectedException
import ru.aigram.media.UploadStore
import ru.aigram.telegram.TelegramGateway
import java.net.URLDecoder

private const val MAX_UPLOAD_NAME_HEADER = 2000

@Component
class UploadEndpoint @Inject constructor(private val uploads: UploadStore, private val telegram: TelegramGateway) {
    fun create(exchange: HttpExchange): UploadedFile {
        val status = telegram.status()
        if (status.state != "ready" || status.userId.isEmpty()) throw TelegramNotConnectedException()
        val name = exchange.requestHeaders.getFirst("X-File-Name").orEmpty()
        if (name.isBlank() || name.length > MAX_UPLOAD_NAME_HEADER) throw InvalidUploadException()
        val decoded = try {
            URLDecoder.decode(name, Charsets.UTF_8)
        } catch (exception: IllegalArgumentException) {
            throw InvalidUploadException()
        }
        val header = exchange.requestHeaders.getFirst("Content-Length")
        val length = if (header == null) -1 else header.toLongOrNull()
        if (length == null || length < -1) throw InvalidUploadException()
        return uploads.create(
            status.userId,
            decoded,
            exchange.requestHeaders.getFirst("Content-Type").orEmpty(),
            exchange.requestBody,
            length,
        )
    }

    fun delete(id: String) {
        val status = telegram.status()
        if (status.state != "ready" || status.userId.isEmpty()) throw TelegramNotConnectedException()
        uploads.remove(status.userId, id)
    }
}
