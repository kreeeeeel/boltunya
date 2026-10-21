package ru.aigram.http

import ru.aigram.domain.MediaFile
import ru.aigram.memory.MemoryTelegramGateway
import ru.aigram.telegram.TelegramGateway
import java.nio.file.Path

class MediaArchiveGateway(private val delegate: MemoryTelegramGateway, private val file: Path) :
    TelegramGateway by delegate {
    var downloads = 0
    override fun media(fileId: Int): MediaFile {
        downloads++
        return MediaFile(file, "image/jpeg", "photo.jpg")
    }
}
