package ru.aigram.ai

import org.springframework.ai.content.Media
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Component
import org.springframework.util.MimeTypeUtils
import ru.aigram.ai.profile.ModelTargetResolver
import ru.aigram.domain.ai.profile.ModelTask
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatRule
import ru.aigram.exception.impl.MediaUnavailableException
import ru.aigram.exception.impl.VisionInputLimitExceededException
import ru.aigram.telegram.TelegramGateway
import java.nio.file.Files

private const val MEBIBYTE = 1024L * 1024
private const val MAX_VISION_FILE_BYTES = 10L * MEBIBYTE
private const val MAX_VISION_TOTAL_BYTES = 20L * MEBIBYTE
private const val MAX_VISION_IMAGES = 10
private val VISION_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")

/** Explicit per-chat consent is checked before downloading or supplying a photograph to a model. */
@Component
class ReplyImageContext(private val telegram: TelegramGateway, private val targets: ModelTargetResolver) {
    fun supports(message: ChatMessage): Boolean =
        message.kind == "photo" && message.media?.mimeType in VISION_MIME_TYPES

    fun build(rule: ChatRule, history: List<ChatMessage>): Map<String, List<Media>> {
        if (!rule.analyzePhotos) {
            return emptyMap()
        }
        val latest = history.lastOrNull { !it.outgoing && !it.deleted && !it.contextExcluded }

        if (latest == null) {
            return emptyMap()
        }

        if (!supports(latest)) {
            return emptyMap()
        }
        val photos = if (latest.mediaAlbumId.isEmpty()) {
            listOf(latest)
        } else {
            history.stream().filter {
                !it.outgoing && !it.deleted && !it.contextExcluded && supports(it) &&
                    it.chatId == latest.chatId && it.topicId == latest.topicId &&
                    it.mediaAlbumId == latest.mediaAlbumId &&
                    (latest.senderId.isEmpty() || it.senderId == latest.senderId)
            }.sorted(compareBy<ChatMessage> { it.date }.thenBy { it.id.toLongOrNull() ?: Long.MAX_VALUE }).toList()
        }
        if (photos.size > MAX_VISION_IMAGES || photos.stream().anyMatch {
                (it.media?.size ?: 0) > MAX_VISION_FILE_BYTES
            } || photos.stream().mapToLong { maxOf(0, it.media?.size ?: 0) }.sum() > MAX_VISION_TOTAL_BYTES
        ) {
            limitExceeded()
        }
        targets.resolve(ModelTask.VISION, rule)
        var remaining = MAX_VISION_TOTAL_BYTES
        val result = linkedMapOf<String, List<Media>>()
        photos.stream().forEach { photo ->
            val (media, bytes) = load(photo, remaining)
            remaining -= bytes
            result[photo.id] = listOf(media)
        }
        return result
    }

    private fun load(message: ChatMessage, remaining: Long): Pair<Media, Long> {
        val attachment = message.media

        if (attachment == null) {
            throw MediaUnavailableException()
        }

        val file = telegram.media(attachment.fileId)
        if (file.mimeType !in VISION_MIME_TYPES) {
            throw MediaUnavailableException()
        }
        val available = minOf(MAX_VISION_FILE_BYTES, remaining)
        if (Files.size(file.path) > available) {
            limitExceeded()
        }
        val bytes = Files.newInputStream(file.path).use { input -> input.readNBytes((available + 1).toInt()) }
        if (bytes.size > available) {
            limitExceeded()
        }
        if (bytes.isEmpty()) {
            throw MediaUnavailableException()
        }
        val media = Media(MimeTypeUtils.parseMimeType(file.mimeType), ByteArrayResource(bytes))
        return media to bytes.size.toLong()
    }

    private fun limitExceeded(): Nothing = throw VisionInputLimitExceededException(
        MAX_VISION_IMAGES,
        MAX_VISION_FILE_BYTES / MEBIBYTE,
        MAX_VISION_TOTAL_BYTES / MEBIBYTE,
    )
}
