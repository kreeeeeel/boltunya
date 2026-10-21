package ru.aigram.component.media

import org.springframework.stereotype.Component
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.automation.ReplyDeliveryPolicy
import ru.aigram.diagnostics.DiagnosticLog
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.events.diagnostics.DiagnosticSource
import ru.aigram.domain.media.SendMediaRequest
import ru.aigram.domain.media.StickerCatalog
import ru.aigram.domain.media.StickerItem
import ru.aigram.exception.impl.ChatNotFoundException
import ru.aigram.exception.impl.InvalidFieldLengthException
import ru.aigram.media.UploadStore
import ru.aigram.storage.SettingsStore
import ru.aigram.telegram.TelegramGateway
import ru.aigram.telegram.TelegramOutgoingEntities
import ru.aigram.validation.InputValidation
import java.io.IOException

private const val REPLY_HISTORY_SIZE = 20
private const val MAX_STICKER_ID_CHARACTERS = 80

@Component
class MediaSendingComponent(
    private val telegram: TelegramGateway,
    private val settings: SettingsStore,
    private val replies: ReplyCoordinator,
    private val delivery: ReplyDeliveryPolicy,
    private val uploads: UploadStore,
    private val validation: InputValidation,
    private val diagnostics: DiagnosticLog,
    private val outgoingEntities: TelegramOutgoingEntities,
) {
    fun send(chatId: String, request: SendMediaRequest) {
        ensureChat(chatId)
        validation.validate(request)
        outgoingEntities.format(request.caption, request.captionEntities)
        val owner = telegram.status().userId
        val files = uploads.begin(owner, request.uploadIds, request.kind, request.caption)
        var success = false
        try {
            cancelReply(chatId)
            val source = telegram.history(chatId, REPLY_HISTORY_SIZE).lastOrNull { !it.outgoing && !it.deleted }
            telegram.sendMedia(
                chatId,
                files.stream().map { file ->
                    if (file.caption.isNotEmpty() && file.caption == request.caption) {
                        file.copy(captionEntities = request.captionEntities)
                    } else {
                        file
                    }
                }.toList(),
                delivery.replyTo(settings.rule(chatId), source?.id.orEmpty()),
                source?.topicId ?: 0,
            )
            success = true
        } finally {
            try {
                uploads.release(owner, request.uploadIds, success)
            } catch (exception: IOException) {
                System.err.println("Attachment cleanup failed: ${exception.javaClass.simpleName}")
                try {
                    diagnostics.record(DiagnosticSource.HTTP, exception)
                } catch (reporting: Exception) {
                    System.err.println("Attachment cleanup diagnostic failed: ${reporting.javaClass.simpleName}")
                }
            }
        }
    }

    fun catalog(): StickerCatalog = telegram.stickers()

    fun stickerSet(id: String): List<StickerItem> = telegram.stickerSet(id)

    fun sticker(chatId: String, value: String) {
        ensureChat(chatId)
        val id = value.trim()
        if (id.isBlank() || id.length > MAX_STICKER_ID_CHARACTERS) {
            throw InvalidFieldLengthException("stickerId", MAX_STICKER_ID_CHARACTERS)
        }
        val source = telegram.history(chatId, REPLY_HISTORY_SIZE).lastOrNull { !it.outgoing && !it.deleted }
        cancelReply(chatId)
        telegram.sendSticker(
            chatId,
            id,
            delivery.replyTo(settings.rule(chatId), source?.id.orEmpty()),
            source?.topicId ?: 0,
        )
    }

    private fun ensureChat(chatId: String) {
        if (telegram.chats().stream().noneMatch { it.id == chatId }) {
            throw ChatNotFoundException()
        }
    }

    private fun cancelReply(chatId: String) {
        replies.onMessage(ChatMessage("manual-${System.nanoTime()}", chatId, "", true, 0))
    }
}
