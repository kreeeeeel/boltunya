package ru.aigram.telegram

import ru.aigram.domain.ChatGallery
import ru.aigram.domain.ChatMessage
import ru.aigram.domain.ChatSummary
import ru.aigram.domain.MediaFile
import ru.aigram.domain.MessageDeletionOptions
import ru.aigram.domain.MessageDeletionResult
import ru.aigram.domain.MessageDeletionScope
import ru.aigram.domain.OutgoingMedia
import ru.aigram.domain.StickerCatalog
import ru.aigram.domain.StickerItem
import ru.aigram.domain.TelegramCredentials
import ru.aigram.domain.TelegramStatus
import ru.aigram.exception.impl.MediaUnavailableException
import ru.aigram.exception.impl.MessageDeletionNotAllowedException

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
    fun messageDeletionOptions(chatId: String, messageId: String): MessageDeletionOptions =
        throw MessageDeletionNotAllowedException()
    fun deleteMessage(chatId: String, messageId: String, scope: MessageDeletionScope): MessageDeletionResult =
        throw MessageDeletionNotAllowedException()
    fun sendSticker(chatId: String, stickerId: String, replyToId: String = "", topicId: Int = 0): String =
        throw MediaUnavailableException()
    fun logout()
}
