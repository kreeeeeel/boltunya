package ru.aigram.telegram

import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatSummary
import ru.aigram.domain.chat.MessageDeletionOptions
import ru.aigram.domain.chat.MessageDeletionResult
import ru.aigram.domain.chat.MessageDeletionScope
import ru.aigram.domain.chat.MessageEditOptions
import ru.aigram.domain.chat.MessageEditRequest
import ru.aigram.domain.chat.MessageEntity
import ru.aigram.domain.media.MediaFile
import ru.aigram.domain.media.StickerCatalog
import ru.aigram.domain.media.StickerItem
import ru.aigram.domain.speech.OutgoingVoiceNote
import ru.aigram.domain.telegram.TelegramCredentials
import ru.aigram.domain.telegram.TelegramStatus
import ru.aigram.exception.impl.MediaUnavailableException
import ru.aigram.exception.impl.MessageDeletionNotAllowedException
import ru.aigram.exception.impl.MessageEditNotAllowedException

interface TelegramGateway : AutoCloseable {
    fun status(): TelegramStatus
    fun connect(credentials: TelegramCredentials)
    fun restore()
    fun submit(value: String)
    fun chats(): List<ChatSummary>
    fun history(chatId: String, limit: Int = 60, before: Long = 0): List<ChatMessage>
    fun send(
        chatId: String,
        text: String,
        replyToId: String = "",
        topicId: Int = 0,
        entities: List<MessageEntity> = emptyList(),
        automated: Boolean = false,
    ): String
    fun typing(chatId: String, active: Boolean)
    fun media(fileId: Int): MediaFile = throw MediaUnavailableException()
    fun stickers(): StickerCatalog = throw MediaUnavailableException()
    fun stickerSet(id: String): List<StickerItem> = throw MediaUnavailableException()
    fun messageLink(chatId: String, messageId: String): String = throw MediaUnavailableException()
    fun messageDeletionOptions(chatId: String, messageId: String): MessageDeletionOptions =
        throw MessageDeletionNotAllowedException()
    fun deleteMessage(chatId: String, messageId: String, scope: MessageDeletionScope): MessageDeletionResult =
        throw MessageDeletionNotAllowedException()
    fun messageEditOptions(chatId: String, messageId: String): MessageEditOptions =
        throw MessageEditNotAllowedException()
    fun editMessage(chatId: String, messageId: String, request: MessageEditRequest): ChatMessage =
        throw MessageEditNotAllowedException()
    fun sendSticker(
        chatId: String,
        stickerId: String,
        replyToId: String = "",
        topicId: Int = 0,
        automated: Boolean = false,
    ): String = throw MediaUnavailableException()
    fun sendVoice(
        chatId: String,
        voice: OutgoingVoiceNote,
        replyToId: String = "",
        topicId: Int = 0,
        automated: Boolean = false,
    ): String = throw MediaUnavailableException()
    fun logout()
}
