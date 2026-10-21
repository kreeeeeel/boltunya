package ru.aigram.speech.reply

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.automation.activity.ReplyTraceScope
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.exception.impl.SpeechInferenceFailedException
import ru.aigram.exception.impl.SpeechInputDisabledException
import ru.aigram.speech.SpeechDownloadClient
import ru.aigram.speech.SpeechEngine
import ru.aigram.speech.SpeechProcessExecutor
import ru.aigram.speech.codec.AudioCodec
import ru.aigram.speech.speechCatalog
import ru.aigram.storage.SettingsStore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame

class SpeechInputContextTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `manual generation on latest incoming voice requires explicitly enabled recognition`() {
        val voice = ChatMessage("17", "42", "Voice message", false, 1, kind = "voice")
        val history = listOf(voice)
        withContext { context ->
            val failure = assertFailsWith<SpeechInputDisabledException> { context.prepare(history) }
            assertEquals(409, failure.status)
            assertEquals("SPEECH_INPUT_DISABLED", failure.code.name)
            assertEquals("Voice message", history.single().text)
            assertFalse(Files.exists(directory.resolve("speech")))
        }
    }

    @Test
    fun `disabled recognition does not reject a text question after an older voice`() {
        val history = listOf(
            ChatMessage("17", "42", "Voice message", false, 1, kind = "voice"),
            ChatMessage("18", "42", "A text question", false, 2),
        )
        withContext { context -> assertSame(history, context.prepare(history)) }
    }

    @Test
    fun `outgoing deleted and excluded voice messages do not replace the actual incoming text question`() {
        val history = listOf(
            ChatMessage("17", "42", "A text question", false, 1),
            ChatMessage("18", "42", "Voice message", true, 2, kind = "voice"),
            ChatMessage("19", "42", "Voice message", false, 3, kind = "voice", deleted = true),
            ChatMessage("20", "42", "Voice message", false, 4, kind = "voice", contextExcluded = true),
        )
        withContext { context -> assertSame(history, context.prepare(history)) }
    }

    private fun withContext(action: (SpeechInputContext) -> Unit) {
        val downloader = SpeechDownloadClient { _, _, _ -> throw SpeechInferenceFailedException() }
        val process = SpeechProcessExecutor { _, _, _ -> throw SpeechInferenceFailedException() }
        speechCatalog(directory, downloader).use { catalog ->
            action(
                SpeechInputContext(
                    directory,
                    SettingsStore(directory),
                    VoiceTestGateway(),
                    SpeechEngine(directory, catalog, process),
                    AudioCodec(directory),
                    ReplyTraceScope(),
                ),
            )
        }
    }
}
