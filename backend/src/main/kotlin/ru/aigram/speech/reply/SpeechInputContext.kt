package ru.aigram.speech.reply

import org.springframework.stereotype.Component
import ru.aigram.automation.activity.ReplyTraceScope
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatRule
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
import java.util.stream.Collectors

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

    fun commitGuard(history: List<ChatMessage>, ownerId: String): () -> Boolean {
        val preferences = settings.get()
        val version = connection.version
        val inputs = history.stream().filter(::eligible).map(ChatMessage::chatId).distinct().collect(
            Collectors.toMap({ it }, { chatId ->
                preferences.voicePreferences(preferences.rules[chatId] ?: ChatRule())
            }),
        )
        return {
            ownerId.isNotEmpty() && !Thread.currentThread().isInterrupted &&
                telegram.status().userId == ownerId && connection.version == version &&
                inputs.entries.stream().allMatch { (chatId, captured) ->
                    val current = settings.get().voicePreferences(settings.rule(chatId))
                    current.inputEnabled == captured.inputEnabled &&
                        current.recognitionModel == captured.recognitionModel
                }
        }
    }

    fun prepare(
        history: List<ChatMessage>,
        rejectDisabledVoice: Boolean = true,
        ownerId: String = telegram.status().userId,
        rule: ChatRule? = null,
    ): List<ChatMessage> {
        val preferences = settings.get()
        val latest = history.lastOrNull { !it.outgoing && !it.deleted && !it.contextExcluded }
        val latestRule = rule ?: preferences.rules[latest?.chatId] ?: ChatRule()
        if (rejectDisabledVoice && latest?.kind == "voice" && !preferences.voicePreferences(latestRule).inputEnabled) {
            throw SpeechInputDisabledException()
        }
        if (history.stream().noneMatch { message ->
                eligible(message) &&
                    preferences.voicePreferences(rule ?: preferences.rules[message.chatId] ?: ChatRule()).inputEnabled
            }
        ) {
            return history
        }
        val version = connection.version
        val prepared = history.stream().map { message ->
            val messageRule = rule ?: preferences.rules[message.chatId] ?: ChatRule()
            val voice = preferences.voicePreferences(messageRule)
            if (!eligible(message) || !voice.inputEnabled) {
                message
            } else {
                val modelId = voice.recognitionModel
                current(ownerId, version, modelId, message.chatId, rule)
                val key = key(ownerId, version, modelId, message)
                val text = transcripts[key] ?: transcribe(message, ownerId, version, modelId, rule).also {
                    if (transcripts.size >= MAX_TRANSCRIPTS) {
                        transcripts.clear()
                    }
                    transcripts[key] = it
                }
                current(ownerId, version, modelId, message.chatId, rule)
                message.copy(text = text, entities = emptyList())
            }
        }.toList()
        return prepared
    }

    private fun eligible(message: ChatMessage): Boolean =
        message.kind == "voice" && !message.deleted && !message.contextExcluded

    private fun transcribe(
        message: ChatMessage,
        ownerId: String,
        version: Long,
        modelId: String,
        rule: ChatRule?,
    ): String {
        val attachment = message.media
        if (attachment == null || attachment.size > MAX_INPUT_BYTES || attachment.duration > MAX_INPUT_SECONDS) {
            throw SpeechAudioLimitException()
        }
        tracing.phase(ActivityPhase.TRANSCRIBING)
        val media = telegram.media(attachment.fileId)
        current(ownerId, version, modelId, message.chatId, rule)
        Files.createDirectories(directory)
        val wave = Files.createTempFile(directory, "recognition-", ".wav")
        try {
            codec.toRecognitionWave(media.path, wave)
            current(ownerId, version, modelId, message.chatId, rule)
            val text = engine.transcribe(wave, modelId).trim()
            current(ownerId, version, modelId, message.chatId, rule)
            if (text.isEmpty()) {
                throw SpeechInferenceFailedException()
            }
            return text
        } finally {
            Files.deleteIfExists(wave)
        }
    }

    private fun current(ownerId: String, version: Long, modelId: String, chatId: String, rule: ChatRule?) {
        val preferences = settings.get().voicePreferences(rule ?: settings.rule(chatId))
        if (ownerId.isEmpty() || Thread.currentThread().isInterrupted || telegram.status().userId != ownerId ||
            connection.version != version || !preferences.inputEnabled ||
            preferences.recognitionModel != modelId
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
