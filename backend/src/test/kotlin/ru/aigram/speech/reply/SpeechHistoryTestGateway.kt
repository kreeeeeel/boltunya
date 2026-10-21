package ru.aigram.speech.reply

import ru.aigram.automation.FakeTelegramGateway
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.media.MediaFile
import ru.aigram.domain.telegram.TelegramStatus
import ru.aigram.exception.impl.MediaUnavailableException
import ru.aigram.telegram.TelegramGateway
import java.nio.file.Path

class SpeechHistoryTestGateway(val delegate: FakeTelegramGateway = FakeTelegramGateway()) :
    TelegramGateway by delegate {
    var owner = "100"
    val files = mutableMapOf<Int, Path>()
    val mediaReads = mutableListOf<Int>()
    val historyReads = mutableListOf<String>()
    var beforeMedia: (Int) -> Unit = {}
    var messages = emptyList<ChatMessage>()

    override fun status(): TelegramStatus = TelegramStatus(state = "ready", userId = owner)

    override fun history(chatId: String, limit: Int, before: Long): List<ChatMessage> {
        historyReads.add(chatId)
        return messages.stream()
            .filter { it.chatId == chatId && (before == 0L || it.id.toLong() < before) }.toList().takeLast(limit)
    }

    override fun media(fileId: Int): MediaFile {
        beforeMedia(fileId)
        mediaReads.add(fileId)
        val file = files[fileId]
        if (file == null) {
            throw MediaUnavailableException()
        }
        return MediaFile(file, "audio/ogg", "synthetic.ogg")
    }
}
