package ru.boltunya.telegram

import ru.boltunya.domain.ChatGallery
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatSummary
import ru.boltunya.domain.MediaFile
import ru.boltunya.domain.OutgoingMedia
import ru.boltunya.domain.StickerCatalog
import ru.boltunya.domain.StickerItem
import ru.boltunya.domain.TelegramCredentials
import ru.boltunya.domain.TelegramStatus
import ru.boltunya.exception.InvalidInputException
import ru.boltunya.exception.impl.MediaUnavailableException

interface TelegramGateway : AutoCloseable {
    fun status(): TelegramStatus
    fun connect(credentials: TelegramCredentials)
    fun restore()
    fun submit(value: String)
    fun chats(): List<ChatSummary>
    fun history(chatId: String, limit: Int = 60, before: Long = 0): List<ChatMessage>
    fun send(chatId: String, text: String, replyToId: String = "", topicId: Int = 0): String
    fun typing(chatId: String, active: Boolean)
    fun media(fileId: Int): MediaFile = throw MediaUnavailableException()
    fun sendMedia(chatId: String, media: List<OutgoingMedia>, replyToId: String = "", topicId: Int = 0): List<String> =
        throw MediaUnavailableException()
    fun stickers(): StickerCatalog = throw MediaUnavailableException()
    fun stickerSet(id: String): List<StickerItem> = throw MediaUnavailableException()
    fun gallery(chatId: String, around: String): ChatGallery = throw MediaUnavailableException()
    fun messageLink(chatId: String, messageId: String): String = throw MediaUnavailableException()
    fun sendSticker(chatId: String, stickerId: String, replyToId: String = "", topicId: Int = 0): String =
        throw MediaUnavailableException()
    fun logout()
}
