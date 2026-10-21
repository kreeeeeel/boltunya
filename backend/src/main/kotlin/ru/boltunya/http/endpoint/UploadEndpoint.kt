package ru.boltunya.http.endpoint

import com.sun.net.httpserver.HttpExchange
import jakarta.inject.Inject
import ru.boltunya.domain.UploadedFile
import ru.boltunya.exception.impl.InvalidUploadException
import ru.boltunya.exception.impl.TelegramNotConnectedException
import ru.boltunya.media.UploadStore
import ru.boltunya.telegram.TelegramGateway
import java.net.URLDecoder

private const val MAX_UPLOAD_NAME_HEADER = 2000

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
