package ru.aigram.speech.reply

import ru.aigram.automation.activity.ReplyTraceScope
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.media.MediaAttachment
import ru.aigram.exception.impl.SpeechInferenceFailedException
import ru.aigram.speech.SpeechDownloadClient
import ru.aigram.speech.SpeechEngine
import ru.aigram.speech.SpeechProcessExecutor
import ru.aigram.speech.installSpeechFixture
import ru.aigram.speech.speechCatalog
import ru.aigram.storage.CredentialStore
import ru.aigram.storage.SettingsStore
import ru.aigram.telegram.TelegramConnectionState
import java.nio.file.Files
import java.nio.file.Path

class SpeechHistoryFixture(val directory: Path, process: SpeechProcessExecutor) : AutoCloseable {
    val settings = SettingsStore(directory)
    val telegram = SpeechHistoryTestGateway()
    val connection = TelegramConnectionState(CredentialStore(directory))
    val tracing = ReplyTraceScope()
    val catalog = speechCatalog(directory, SpeechDownloadClient { _, _, _ -> throw SpeechInferenceFailedException() })
    val codec = SpeechHistoryTestCodec(directory)
    val engine = SpeechEngine(directory, catalog, process)
    val context = SpeechInputContext(directory, settings, telegram, engine, codec, tracing, connection)

    fun enable() {
        listOf("sherpa-runtime", "whisper-tiny").stream().forEach { installSpeechFixture(directory, it) }
        settings.update { it.copy(voiceInputEnabled = true) }
    }

    fun voice(id: Int, text: String, outgoing: Boolean = false, automated: Boolean = false): ChatMessage {
        Files.createDirectories(directory)
        telegram.files[id] = Files.writeString(directory.resolve("synthetic-$id.ogg"), text)
        return ChatMessage(
            "$id",
            "42",
            "Voice placeholder",
            outgoing,
            id.toLong(),
            kind = "voice",
            automated = automated,
            media = MediaAttachment(id, "audio/ogg", "synthetic.ogg", size = 100, duration = 2, uniqueId = "file-$id"),
        )
    }

    override fun close() = catalog.close()
}
