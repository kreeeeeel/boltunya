package ru.aigram.telegram

import it.tdlight.jni.TdApi
import jakarta.inject.Inject
import ru.aigram.domain.ChatMessage
import ru.aigram.domain.MediaAttachment
import java.util.stream.IntStream

class TelegramMessageMapper @Inject constructor(
    private val files: TelegramMediaStore,
    private val entities: TelegramEntityMapper = TelegramEntityMapper(),
) {

    fun map(message: TdApi.Message): ChatMessage {
        val content = message.content
        val kind = when (content) {
            is TdApi.MessageText -> "text"
            is TdApi.MessagePhoto -> "photo"
            is TdApi.MessageSticker -> "sticker"
            is TdApi.MessageVideo -> "video"
            is TdApi.MessageVideoNote -> "videoNote"
            is TdApi.MessageAnimation -> "animation"
            is TdApi.MessageVoiceNote -> "voice"
            is TdApi.MessageAudio -> "audio"
            is TdApi.MessageDocument -> "document"
            else -> "unsupported"
        }
        val formatted = when (content) {
            is TdApi.MessageText -> content.text
            is TdApi.MessagePhoto -> content.caption
            is TdApi.MessageVideo -> content.caption
            is TdApi.MessageAnimation -> content.caption
            is TdApi.MessageVoiceNote -> content.caption
            is TdApi.MessageAudio -> content.caption
            is TdApi.MessageDocument -> content.caption
            else -> TdApi.FormattedText("", emptyArray())
        }
        val media = when (content) {
            is TdApi.MessagePhoto -> content.photo.sizes.maxByOrNull { it.width * it.height }?.let {
                attachment(it.photo, "image/jpeg", "photo.jpg", it.width, it.height)
            }
            is TdApi.MessageSticker -> {
                val sticker = content.sticker
                val mime = when (sticker.format) {
                    is TdApi.StickerFormatTgs -> "application/x-tgsticker"
                    is TdApi.StickerFormatWebm -> "video/webm"
                    else -> "image/webp"
                }
                attachment(sticker.sticker, mime, "sticker", sticker.width, sticker.height, sticker.thumbnail)
            }
            is TdApi.MessageVideo -> content.video.let {
                attachment(it.video, "video/mp4", it.fileName, it.width, it.height, it.thumbnail)
                    .copy(duration = it.duration)
            }
            is TdApi.MessageVideoNote -> content.videoNote.let {
                attachment(it.video, "video/mp4", "video-note.mp4", it.length, it.length, it.thumbnail)
                    .copy(duration = it.duration)
            }
            is TdApi.MessageAnimation -> content.animation.let {
                attachment(it.animation, it.mimeType, it.fileName, it.width, it.height, it.thumbnail)
            }
            is TdApi.MessageVoiceNote -> attachment(content.voiceNote.voice, "audio/ogg", "voice.ogg").copy(
                duration = content.voiceNote.duration,
                waveform = IntStream.range(0, content.voiceNote.waveform.size)
                    .map { content.voiceNote.waveform[it].toInt() and 0xff }.boxed().toList(),
            )
            is TdApi.MessageAudio -> attachment(content.audio.audio, content.audio.mimeType, content.audio.fileName)
                .copy(duration = content.audio.duration)
            is TdApi.MessageDocument -> attachment(
                content.document.document,
                "application/octet-stream",
                content.document.fileName,
            )
            else -> null
        }
        return ChatMessage(
            message.id.toString(),
            message.chatId.toString(),
            formatted.text,
            message.isOutgoing,
            message.date.toLong(),
            kind,
            media,
            entities.map(formatted),
            mediaAlbumId = message.mediaAlbumId.takeIf { it != 0L }?.toString().orEmpty(),
        )
    }

    fun preview(message: TdApi.Message): String {
        val mapped = map(message)
        val label = when (mapped.kind) {
            "photo" -> "Фото"
            "sticker" -> "Стикер"
            "video" -> "Видео"
            "videoNote" -> "Видеосообщение"
            "animation" -> "GIF"
            "voice" -> "Голосовое сообщение"
            "audio" -> "Аудио"
            "document" -> mapped.media?.fileName.orEmpty()
            "text" -> ""
            else -> "Сообщение"
        }
        return listOf(label, mapped.text).filter(String::isNotBlank).joinToString(" · ")
    }

    private fun attachment(
        file: TdApi.File,
        mime: String,
        name: String,
        width: Int = 0,
        height: Int = 0,
        thumbnail: TdApi.Thumbnail? = null,
    ): MediaAttachment = MediaAttachment(
        fileId = files.register(file, mime, name),
        mimeType = mime,
        fileName = name,
        width = width,
        height = height,
        thumbnailFileId = thumbnail?.let { files.register(it.file, "image/jpeg", "thumbnail.jpg") } ?: 0,
        size = maxOf(file.size, file.expectedSize),
        downloaded = files.isDownloaded(file.id),
        cacheVersion = files.version(),
        uniqueId = file.remote?.uniqueId.orEmpty(),
    )
}
