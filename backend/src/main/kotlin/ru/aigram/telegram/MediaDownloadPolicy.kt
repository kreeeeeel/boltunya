package ru.aigram.telegram

import jakarta.inject.Inject
import org.springframework.stereotype.Component
import ru.aigram.domain.ChatMessage
import ru.aigram.domain.MediaAttachment
import ru.aigram.storage.SettingsStore

private const val BYTES_PER_MB = 1024L * 1024

@Component
class MediaDownloadPolicy @Inject constructor(private val settings: SettingsStore) {
    fun allows(kind: String, size: Long): Boolean {
        if (kind == "sticker") return true
        val current = settings.get()
        if (size > current.autoDownloadMaxMb * BYTES_PER_MB) return false
        return when (kind) {
            "photo" -> current.autoDownloadPhotos
            "video", "videoNote", "animation" -> current.autoDownloadVideos
            "voice", "audio" -> current.autoDownloadAudio
            "document" -> current.autoDownloadFiles
            else -> false
        }
    }

    fun decorate(message: ChatMessage, files: TelegramMediaStore): ChatMessage {
        val media = message.media ?: return message
        if (files.conflicts(media)) return message.copy(media = null)
        files.remember(media)
        val thumbnailId = if (message.deleted) 0 else media.thumbnailFileId
        if (thumbnailId != 0) {
            files.remember(MediaAttachment(thumbnailId, "image/jpeg", "thumbnail.jpg"))
        }
        return message.copy(
            media = media.copy(
                autoDownload = allows(message.kind, media.size),
                thumbnailFileId = thumbnailId,
                downloaded = files.isDownloaded(media.fileId),
                cacheVersion = files.version(),
            ),
        )
    }
}
