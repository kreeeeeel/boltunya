package ru.aigram.controller.integration

import ru.aigram.domain.media.MediaFile
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
