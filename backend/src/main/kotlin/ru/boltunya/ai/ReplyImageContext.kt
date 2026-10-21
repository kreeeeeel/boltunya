package ru.boltunya.ai

import jakarta.inject.Inject
import org.springframework.ai.content.Media
import org.springframework.core.io.ByteArrayResource
import org.springframework.util.MimeTypeUtils
import ru.boltunya.ai.profile.ModelTargetResolver
import ru.boltunya.ai.profile.ModelTask
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.exception.impl.MediaTooLargeException
import ru.boltunya.exception.impl.MediaUnavailableException
import ru.boltunya.telegram.TelegramGateway
import java.nio.file.Files

private const val MAX_VISION_BYTES = 10L * 1024 * 1024
private val VISION_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")

/** Explicit per-chat consent is checked before downloading or supplying a photograph to a model. */
class ReplyImageContext @Inject constructor(
    private val telegram: TelegramGateway,
    private val targets: ModelTargetResolver,
) {
    fun supports(message: ChatMessage): Boolean =
        message.kind == "photo" && message.media?.mimeType in VISION_MIME_TYPES

    fun build(rule: ChatRule, history: List<ChatMessage>): Map<String, List<Media>> {
        if (!rule.analyzePhotos) return emptyMap()
        val latest = history.lastOrNull { !it.outgoing && !it.deleted } ?: return emptyMap()
        if (!supports(latest)) return emptyMap()
        targets.resolve(ModelTask.VISION, rule)
        val attachment = latest.media ?: throw MediaUnavailableException()
        if (attachment.size > MAX_VISION_BYTES) throw MediaTooLargeException()
        val file = telegram.media(attachment.fileId)
        if (file.mimeType !in VISION_MIME_TYPES) throw MediaUnavailableException()
        if (Files.size(file.path) > MAX_VISION_BYTES) throw MediaTooLargeException()
        val bytes = Files.readAllBytes(file.path)
        if (bytes.isEmpty()) throw MediaUnavailableException()
        val media = Media(MimeTypeUtils.parseMimeType(file.mimeType), ByteArrayResource(bytes))
        return mapOf(latest.id to listOf(media))
    }
}
