package ru.aigram.component.speech

import org.springframework.stereotype.Component
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.automation.ReplyDeliveryPolicy
import ru.aigram.domain.ai.PreviewJob
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.media.MediaFile
import ru.aigram.domain.speech.SpeechAssetKind
import ru.aigram.exception.impl.ChatNotFoundException
import ru.aigram.exception.impl.InvalidSpeechRequestException
import ru.aigram.speech.SpeechAssetCatalog
import ru.aigram.speech.reply.VoiceDraftStore
import ru.aigram.speech.reply.VoiceReplyService
import ru.aigram.speech.reply.voicePreferences
import ru.aigram.storage.SettingsStore
import ru.aigram.telegram.TelegramGateway

private const val MAX_VOICE_TEXT = 4096
private const val VOICE_REPLY_HISTORY = 20

@Component
class SpeechReplyComponent(
    private val telegram: TelegramGateway,
    private val settings: SettingsStore,
    private val replies: ReplyCoordinator,
    private val delivery: ReplyDeliveryPolicy,
    private val voices: VoiceReplyService,
    private val drafts: VoiceDraftStore,
    private val catalog: SpeechAssetCatalog,
) {
    fun enqueue(chatId: String, text: String, modelId: String? = null, speed: Double? = null): PreviewJob {
        ensureChat(chatId)
        if (text.isBlank() || text.length > MAX_VOICE_TEXT) {
            throw InvalidSpeechRequestException()
        }
        val preferences = settings.get().voicePreferences(settings.rule(chatId))
        val ownerId = telegram.status().userId
        val selectedModel = modelId ?: preferences.synthesisModel
        val selectedSpeed = speed ?: preferences.speechRate
        if (!selectedSpeed.isFinite() || selectedSpeed !in 0.5..2.0 ||
            selectedModel.isBlank() || selectedModel.length > 120
        ) {
            throw InvalidSpeechRequestException()
        }
        catalog.model(selectedModel, SpeechAssetKind.SYNTHESIS)
        return replies.enqueueDraft(chatId, selectedModel) {
            voices.draft(text, chatId, ownerId, selectedModel, selectedSpeed)
        }
    }

    fun audio(id: String): MediaFile = MediaFile(drafts.audio(id), "audio/ogg", "voice.ogg")

    fun send(chatId: String, voiceId: String, replyToId: String?, topicId: Int?) {
        ensureChat(chatId)
        val source = telegram.history(chatId, VOICE_REPLY_HISTORY).lastOrNull { !it.outgoing && !it.deleted }
        val reply = if (replyToId.isNullOrEmpty()) {
            delivery.replyTo(settings.rule(chatId), source?.id.orEmpty())
        } else {
            replyToId
        }
        drafts.send(chatId, voiceId, reply, topicId ?: source?.topicId ?: 0, automated = false) {
            replies.onMessage(ChatMessage("manual-${System.nanoTime()}", chatId, "", true, 0))
        }
    }

    private fun ensureChat(chatId: String) {
        if (telegram.chats().stream().noneMatch { it.id == chatId }) {
            throw ChatNotFoundException()
        }
    }
}
