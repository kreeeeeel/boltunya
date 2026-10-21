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
) {
    private val directory = directory.resolve("speech/recognition")
    private val transcripts = ConcurrentHashMap<String, String>()

    fun prepare(history: List<ChatMessage>): List<ChatMessage> {
        val owner = telegram.status().userId
        val preferences = settings.get()
        val latest = history.lastOrNull { !it.outgoing && !it.deleted && !it.contextExcluded }
        if (!preferences.voiceInputEnabled) {
            if (latest?.kind == "voice") {
                throw SpeechInputDisabledException()
            }
            return history
        }
        if (latest?.kind == "voice" && !transcripts.containsKey(key(owner, latest))) {
            val attachment = latest.media
            if (attachment == null || attachment.size > MAX_INPUT_BYTES || attachment.duration > MAX_INPUT_SECONDS) {
                throw SpeechAudioLimitException()
            }
            tracing.phase(ActivityPhase.TRANSCRIBING)
            val media = telegram.media(attachment.fileId)
            Files.createDirectories(directory)
            val wave = Files.createTempFile(directory, "recognition-", ".wav")
            try {
                codec.toRecognitionWave(media.path, wave)
                val text = engine.transcribe(wave, preferences.voiceRecognitionModel).trim()
                if (text.isEmpty()) {
                    throw SpeechInferenceFailedException()
                }
                if (Thread.currentThread().isInterrupted || telegram.status().userId != owner ||
                    !settings.get().voiceInputEnabled ||
                    settings.get().voiceRecognitionModel != preferences.voiceRecognitionModel
                ) {
                    throw ReplyCancelledException()
                }
                if (transcripts.size >= MAX_TRANSCRIPTS) {
                    transcripts.clear()
                }
                transcripts[key(owner, latest)] = text
            } finally {
                Files.deleteIfExists(wave)
            }
        }
        return history.stream().map { message ->
            val text = if (message.deleted || message.contextExcluded) {
                null
            } else {
                transcripts[key(owner, message)]
            }
            if (text == null) {
                message
            } else {
                message.copy(text = text, entities = emptyList())
            }
        }.toList()
    }

    private fun key(owner: String, message: ChatMessage): String = listOf(
        owner,
        message.chatId,
        message.id,
        message.media?.uniqueId.orEmpty(),
        message.media?.fileId.toString(),
        message.media?.cacheVersion.toString(),
        settings.get().voiceRecognitionModel,
    ).joinToString("\u0000")
}
