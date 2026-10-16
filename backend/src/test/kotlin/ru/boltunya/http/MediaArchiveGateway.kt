package ru.boltunya.http

import ru.boltunya.domain.MediaFile
import ru.boltunya.memory.MemoryTelegramGateway
import ru.boltunya.telegram.TelegramGateway
import java.nio.file.Path

class MediaArchiveGateway(private val delegate: MemoryTelegramGateway, private val file: Path) :
    TelegramGateway by delegate {
    var downloads = 0
    override fun media(fileId: Int): MediaFile {
        downloads++
        return MediaFile(file, "image/jpeg", "photo.jpg")
    }
}
