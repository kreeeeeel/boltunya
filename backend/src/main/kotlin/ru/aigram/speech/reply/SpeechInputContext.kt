package ru.aigram.speech.reply

import org.springframework.stereotype.Component
import ru.aigram.automation.activity.ReplyTraceScope
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.events.ActivityPhase
import ru.aigram.exception.impl.ReplyCancelledException
import ru.aigram.exception.impl.SpeechAudioLimitException
import ru.aigram.exception.impl.SpeechInferenceFailedException
import ru.aigram.exception.impl.SpeechInputDisabledException
import ru.aigram.speech.SpeechEngine
import ru.aigram.speech.codec.AudioCodec
import ru.aigram.storage.SettingsStore
import ru.aigram.telegram.TelegramConnectionState
import ru.aigram.telegram.TelegramGateway
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

private const val MAX_TRANSCRIPTS = 1000
private const val MAX_INPUT_BYTES = 25L * 1024 * 1024
private const val MAX_INPUT_SECONDS = 600

@Component
class SpeechInputContext(
    directory: Path,
    private val settings: SettingsStore,
    private val telegram: TelegramGateway,
    private val engine: SpeechEngine,
    private val codec: AudioCodec,
    private val tracing: ReplyTraceScope,
    private val connection: TelegramConnectionState,
) {
    private val directory = directory.resolve("speech/recognition")
    private val transcripts = ConcurrentHashMap<String, String>()

    fun prepare(
        history: List<ChatMessage>,
        rejectDisabledVoice: Boolean = true,
        ownerId: String = telegram.status().userId,
    ): List<ChatMessage> {
        val preferences = settings.get()
        val latest = history.lastOrNull { !it.outgoing && !it.deleted && !it.contextExcluded }
        if (!preferences.voiceInputEnabled) {
            if (rejectDisabledVoice && latest?.kind == "voice") {
                throw SpeechInputDisabledException()
            }
            return history
        }
        if (history.stream().noneMatch { eligible(it) }) {
            return history
        }
        val version = connection.version
        val modelId = preferences.voiceRecognitionModel
        val prepared = history.stream().map { message ->
            current(ownerId, version, modelId)
            if (!eligible(message)) {
                message
            } else {
                val key = key(ownerId, version, modelId, message)
                val text = transcripts[key] ?: transcribe(message, ownerId, version, modelId).also {
                    if (transcripts.size >= MAX_TRANSCRIPTS) {
                        transcripts.clear()
                    }
                    transcripts[key] = it
                }
                message.copy(text = text, entities = emptyList())
            }
        }.toList()
        current(ownerId, version, modelId)
        return prepared
    }

    private fun eligible(message: ChatMessage): Boolean =
        message.kind == "voice" && !message.deleted && !message.contextExcluded

    private fun transcribe(message: ChatMessage, ownerId: String, version: Long, modelId: String): String {
        val attachment = message.media
        if (attachment == null || attachment.size > MAX_INPUT_BYTES || attachment.duration > MAX_INPUT_SECONDS) {
            throw SpeechAudioLimitException()
        }
        tracing.phase(ActivityPhase.TRANSCRIBING)
        val media = telegram.media(attachment.fileId)
        current(ownerId, version, modelId)
        Files.createDirectories(directory)
        val wave = Files.createTempFile(directory, "recognition-", ".wav")
        try {
            codec.toRecognitionWave(media.path, wave)
            current(ownerId, version, modelId)
            val text = engine.transcribe(wave, modelId).trim()
            current(ownerId, version, modelId)
            if (text.isEmpty()) {
                throw SpeechInferenceFailedException()
            }
            return text
        } finally {
            Files.deleteIfExists(wave)
        }
    }

    private fun current(ownerId: String, version: Long, modelId: String) {
        val preferences = settings.get()
        if (ownerId.isEmpty() || Thread.currentThread().isInterrupted || telegram.status().userId != ownerId ||
            connection.version != version || !preferences.voiceInputEnabled ||
            preferences.voiceRecognitionModel != modelId
        ) {
            throw ReplyCancelledException()
        }
    }

    private fun key(owner: String, version: Long, modelId: String, message: ChatMessage): String = listOf(
        owner,
        version.toString(),
        message.chatId,
        message.id,
        message.editedAt.toString(),
        message.media?.uniqueId.orEmpty(),
        message.media?.fileId.toString(),
        message.media?.cacheVersion.toString(),
        modelId,
    ).joinToString("\u0000")
}
