package ru.aigram.http.endpoint

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.automation.ReplyDeliveryPolicy
import ru.aigram.diagnostics.DiagnosticLog
import ru.aigram.diagnostics.DiagnosticSource
import ru.aigram.domain.ChatMessage
import ru.aigram.domain.SendMediaRequest
import ru.aigram.domain.StickerCatalog
import ru.aigram.domain.StickerItem
import ru.aigram.exception.impl.ChatNotFoundException
import ru.aigram.http.InputValidation
import ru.aigram.http.RequestReader
import ru.aigram.media.UploadStore
import ru.aigram.storage.SettingsStore
import ru.aigram.telegram.TelegramGateway
import java.io.IOException

private const val REPLY_HISTORY_SIZE = 20

class AttachmentEndpoint @Inject constructor(
    private val json: Json,
    private val telegram: TelegramGateway,
    private val settings: SettingsStore,
    private val replies: ReplyCoordinator,
    private val delivery: ReplyDeliveryPolicy,
    private val uploads: UploadStore,
    private val validation: InputValidation,
    private val input: RequestReader,
    private val diagnostics: DiagnosticLog,
) {
    fun send(chatId: String, body: JsonObject) {
        ensureChat(chatId)
        val request = json.decodeFromJsonElement<SendMediaRequest>(body)
        validation.validate(request)
        val owner = telegram.status().userId
        val files = uploads.begin(owner, request.uploadIds, request.kind, request.caption)
        var success = false
        try {
            cancelReply(chatId)
            val source = telegram.history(chatId, REPLY_HISTORY_SIZE).lastOrNull { !it.outgoing && !it.deleted }
            telegram.sendMedia(
                chatId,
                files,
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

    fun sticker(chatId: String, body: JsonObject) {
        ensureChat(chatId)
        val id = input.text(body, "stickerId", 80)
        val source = telegram.history(chatId, REPLY_HISTORY_SIZE).lastOrNull { !it.outgoing && !it.deleted }
        cancelReply(chatId)
        telegram.sendSticker(
            chatId,
            id,
            delivery.replyTo(settings.rule(chatId), source?.id.orEmpty()),
            source?.topicId ?: 0,
        )
    }

    private fun ensureChat(id: String) {
        if (telegram.chats().stream().noneMatch { it.id == id }) throw ChatNotFoundException()
    }

    private fun cancelReply(chatId: String) {
        replies.onMessage(ChatMessage("manual-${System.nanoTime()}", chatId, "", true, 0))
    }
}
