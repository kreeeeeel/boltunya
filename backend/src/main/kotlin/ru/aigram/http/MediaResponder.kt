package ru.aigram.http

import com.sun.net.httpserver.HttpExchange
import jakarta.inject.Inject
import ru.aigram.domain.MediaFile
import ru.aigram.exception.impl.AnimationTooLargeException
import java.net.URLEncoder
import java.nio.file.Files
import java.util.stream.Stream
import java.util.zip.GZIPInputStream

private const val MAX_ANIMATION_BYTES = 2 * 1024 * 1024

class MediaResponder @Inject constructor() {
    fun respond(exchange: HttpExchange, media: MediaFile) {
        exchange.responseHeaders.set("Cache-Control", "private, no-store")
        if (media.mimeType in setOf("application/x-tgsticker", "application/x-tgwallpattern")) {
            val bytes = GZIPInputStream(Files.newInputStream(media.path)).use { it.readNBytes(MAX_ANIMATION_BYTES + 1) }
            if (bytes.size > MAX_ANIMATION_BYTES) {
                throw AnimationTooLargeException()
            }
            exchange.responseHeaders.set(
                "Content-Type",
                if (media.mimeType ==
                    "application/x-tgwallpattern"
                ) {
                    "image/svg+xml"
                } else {
                    "application/json"
                },
            )
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.write(bytes)
            return
        }
        val size = Files.size(media.path)
        val range = exchange.requestHeaders.getFirst("Range")
        val bounds = range?.let { byteRange(it, size) }
        val start = bounds?.first ?: 0
        val end = bounds?.last ?: (size - 1)
        if ((range != null && bounds == null) || start >= size || end < start) {
            exchange.responseHeaders.set("Content-Range", "bytes */$size")
            exchange.sendResponseHeaders(416, -1)
            return
        }
        exchange.responseHeaders.set("Content-Type", media.mimeType)
        exchange.responseHeaders.set("Accept-Ranges", "bytes")
        if (media.mimeType == "application/octet-stream") {
            val name = URLEncoder.encode(media.fileName, Charsets.UTF_8).replace("+", "%20")
            exchange.responseHeaders.set("Content-Disposition", "attachment; filename*=UTF-8''$name")
        }
        if (range != null) {
            exchange.responseHeaders.set("Content-Range", "bytes $start-$end/$size")
        }
        exchange.sendResponseHeaders(if (range == null) 200 else 206, end - start + 1)
        Files.newInputStream(media.path).use { input ->
            input.skipNBytes(start)
            var remaining = end - start + 1
            val buffer = ByteArray(64 * 1024)
            Stream.generate { input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt()) }
                .takeWhile { it > 0 }
                .forEach { count ->
                    exchange.responseBody.write(buffer, 0, count)
                    remaining -= count
                }
        }
    }

    private fun byteRange(value: String, size: Long): LongRange? {
        if (!value.startsWith("bytes=")) return null
        val parts = value.substring(6).split('-', limit = 2)
        if (parts.size != 2 || parts[0].isEmpty() || !parts.stream().allMatch { part ->
                part.chars().allMatch { it in '0'.code..'9'.code }
            }
        ) {
            return null
        }
        val start = parts[0].toLongOrNull() ?: return null
        val end = if (parts[1].isEmpty()) size - 1 else parts[1].toLongOrNull()?.coerceAtMost(size - 1) ?: return null
        return start..end
    }
}
