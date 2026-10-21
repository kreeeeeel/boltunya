package ru.aigram.ai

import ru.aigram.automation.FakeTelegramGateway
import ru.aigram.domain.media.MediaFile
import ru.aigram.telegram.TelegramGateway
import java.nio.file.Path

class VisionAlbumGateway(private val files: Map<Int, Path>) : TelegramGateway by FakeTelegramGateway() {
    val downloads = mutableListOf<Int>()

    override fun media(fileId: Int): MediaFile {
        downloads.add(fileId)
        val path = files[fileId] ?: error("Missing synthetic image")
        return MediaFile(path, "image/jpeg", "synthetic.jpg")
    }
}
