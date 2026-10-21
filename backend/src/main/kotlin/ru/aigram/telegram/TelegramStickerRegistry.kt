package ru.aigram.telegram

import it.tdlight.jni.TdApi
import jakarta.inject.Inject
import ru.aigram.domain.MediaAttachment
import ru.aigram.domain.StickerItem
import ru.aigram.domain.StickerSet
import ru.aigram.exception.impl.UnknownMediaException
import java.util.concurrent.ConcurrentHashMap

class TelegramStickerRegistry @Inject constructor(private val files: TelegramMediaStore) {
    private var ownerId = ""
    private val stickers = ConcurrentHashMap<String, StickerItem>()
    private val sets = ConcurrentHashMap.newKeySet<Long>()

    @Synchronized
    fun activate(owner: String) {
        if (ownerId == owner) return
        ownerId = owner
        stickers.clear()
        sets.clear()
    }

    fun remember(sticker: TdApi.Sticker): StickerItem? {
        val file = sticker.sticker ?: return null
        if (file.id <= 0 || sticker.fullType !is TdApi.StickerFullTypeRegular) return null
        val mime = when (sticker.format) {
            is TdApi.StickerFormatTgs -> "application/x-tgsticker"
            is TdApi.StickerFormatWebm -> "video/webm"
            else -> "image/webp"
        }
        val thumbnail = sticker.thumbnail?.let { files.register(it.file, "image/webp", "sticker-thumbnail.webp") } ?: 0
        val media = MediaAttachment(
            fileId = files.register(file, mime, "sticker"),
            mimeType = mime,
            fileName = "sticker",
            width = sticker.width,
            height = sticker.height,
            thumbnailFileId = thumbnail,
            size = maxOf(file.size, file.expectedSize),
            downloaded = files.isDownloaded(file.id),
            cacheVersion = files.version(),
            uniqueId = file.remote?.uniqueId.orEmpty(),
        )
        val item =
            StickerItem(
                file.id.toString(),
                sticker.emoji.orEmpty(),
                sticker.setId.toString(),
                media,
                sticker.width,
                sticker.height,
            )
        stickers[item.id] = item
        if (sticker.setId > 0) sets.add(sticker.setId)
        return item
    }

    fun remember(info: TdApi.StickerSetInfo): StickerSet {
        sets.add(info.id)
        val cover = info.covers.orEmpty().asList().stream().map(::remember).filter {
            it != null
        }.findFirst().orElse(null)
        return StickerSet(info.id.toString(), info.title.orEmpty(), info.name.orEmpty(), cover?.media, info.size)
    }

    fun allowsSet(id: Long): Boolean = id in sets

    fun resolve(id: String): StickerItem {
        val item = stickers[id] ?: throw UnknownMediaException()
        if (files.conflicts(item.media)) throw UnknownMediaException()
        return item
    }

    fun content(id: String): TdApi.InputMessageSticker {
        val item = resolve(id)
        return TdApi.InputMessageSticker(
            TdApi.InputSticker(TdApi.InputFileId(item.media.fileId), null, item.width, item.height),
            item.emoji,
        )
    }
}
