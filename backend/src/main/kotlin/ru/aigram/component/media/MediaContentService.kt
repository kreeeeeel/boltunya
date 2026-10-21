package ru.aigram.component.media

import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import ru.aigram.domain.media.MediaFile
import ru.aigram.exception.impl.AnimationTooLargeException
import ru.aigram.telegram.TelegramGateway
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.nio.file.Files
import java.util.zip.GZIPInputStream

private const val MAX_ANIMATION_BYTES = 2 * 1024 * 1024
private val COMPRESSED_MEDIA_TYPES = setOf("application/x-tgsticker", "application/x-tgwallpattern")

@Service
class MediaContentService(private val telegram: TelegramGateway) {
    fun respond(fileId: Int, request: HttpServletRequest): ResponseEntity<Resource> =
        respond(telegram.media(fileId), request)

    fun respond(media: MediaFile, request: HttpServletRequest): ResponseEntity<Resource> {
        if (media.mimeType in COMPRESSED_MEDIA_TYPES) {
            return animation(media)
        }

        val size = Files.size(media.path)
        val header = request.getHeader(HttpHeaders.RANGE)
        val bounds = header?.let { range(it, size) }
        if (size == 0L || header != null && bounds == null) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .header(HttpHeaders.CONTENT_RANGE, "bytes */$size")
                .build()
        }

        val response = ResponseEntity.status(
            if (bounds == null) {
                HttpStatus.OK
            } else {
                HttpStatus.PARTIAL_CONTENT
            },
        )
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
            .contentType(MediaType.parseMediaType(media.mimeType))

        if (media.mimeType == MediaType.APPLICATION_OCTET_STREAM_VALUE) {
            val name = URLEncoder.encode(media.fileName, Charsets.UTF_8).replace("+", "%20")
            response.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''$name")
        }

        if (bounds == null) {
            return response.contentLength(size).body(FileSystemResource(media.path))
        }

        val input = Files.newInputStream(media.path)
        try {
            input.skipNBytes(bounds.first)
            val length = bounds.last - bounds.first + 1
            val resource = InputStreamResource(RangedInputStream(input, length))
            return response.header(HttpHeaders.CONTENT_RANGE, "bytes ${bounds.first}-${bounds.last}/$size")
                .contentLength(length)
                .body(resource)
        } catch (exception: Exception) {
            input.close()
            throw exception
        }
    }

    private fun animation(media: MediaFile): ResponseEntity<Resource> {
        val bytes = Files.newInputStream(media.path).use { input ->
            GZIPInputStream(input).use { it.readNBytes(MAX_ANIMATION_BYTES + 1) }
        }
        if (bytes.size > MAX_ANIMATION_BYTES) {
            throw AnimationTooLargeException()
        }

        val type = if (media.mimeType == "application/x-tgwallpattern") {
            "image/svg+xml"
        } else {
            "application/json"
        }

        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .contentType(MediaType.parseMediaType(type))
            .contentLength(bytes.size.toLong())
            .body(InputStreamResource(ByteArrayInputStream(bytes)))
    }

    private fun range(value: String, size: Long): LongRange? {
        if (!value.startsWith("bytes=")) {
            return null
        }

        val parts = value.substringAfter("bytes=").split('-', limit = 2)
        if (parts.size != 2 || parts[0].isEmpty() ||
            !parts.stream().allMatch { part -> part.chars().allMatch { it in '0'.code..'9'.code } }
        ) {
            return null
        }

        val start = parts[0].toLongOrNull()
        val end = if (parts[1].isEmpty()) {
            size - 1
        } else {
            parts[1].toLongOrNull()?.coerceAtMost(size - 1)
        }
        if (start == null || end == null || start >= size || end < start) {
            return null
        }

        return start..end
    }
}
