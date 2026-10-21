package ru.aigram.component.media

import org.springframework.stereotype.Component
import ru.aigram.domain.media.UploadedFile
import ru.aigram.exception.impl.InvalidUploadException
import ru.aigram.exception.impl.TelegramNotConnectedException
import ru.aigram.media.UploadStore
import ru.aigram.telegram.TelegramGateway
import java.net.URLDecoder

private const val MAX_UPLOAD_NAME_HEADER = 2000

@Component
class UploadComponent(private val uploads: UploadStore, private val telegram: TelegramGateway) {
    fun create(name: String, contentType: String, body: ByteArray, expectedLength: Long): UploadedFile {
        val owner = owner()
        if (name.isBlank() || name.length > MAX_UPLOAD_NAME_HEADER || expectedLength < -1) {
            throw InvalidUploadException()
        }
        val decoded = try {
            URLDecoder.decode(name, Charsets.UTF_8)
        } catch (exception: IllegalArgumentException) {
            throw InvalidUploadException()
        }
        return uploads.create(owner, decoded, contentType, body.inputStream(), expectedLength)
    }

    fun delete(id: String) {
        uploads.remove(owner(), id)
    }

    fun download(fileId: Int) {
        telegram.media(fileId)
    }

    private fun owner(): String {
        val status = telegram.status()
        if (status.state != "ready" || status.userId.isEmpty()) {
            throw TelegramNotConnectedException()
        }
        return status.userId
    }
}
