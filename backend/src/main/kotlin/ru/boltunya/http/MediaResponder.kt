package ru.boltunya.http

import com.sun.net.httpserver.HttpExchange
import jakarta.inject.Inject
import ru.boltunya.domain.MediaFile
import ru.boltunya.exception.InvalidInputException
import ru.boltunya.exception.impl.AnimationTooLargeException
import ru.boltunya.resources.ResourceCatalog
import java.net.URLEncoder
import java.nio.file.Files
import java.util.stream.Stream
import java.util.zip.GZIPInputStream

private const val MAX_ANIMATION_BYTES = 2 * 1024 * 1024

private val resources = ResourceCatalog()

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
        val match = range?.let { resources.regex("common/MediaResponder-1").matchEntire(it) }
        val start = match?.groupValues?.get(1)?.toLongOrNull() ?: 0
        val end = match?.groupValues?.get(2)?.toLongOrNull()?.coerceAtMost(size - 1) ?: (size - 1)
        if ((range != null && match == null) || start >= size || end < start) {
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
}
