package ru.aigram.speech.reply

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.chat.VoiceSettingsMode
import ru.aigram.exception.impl.ReplyCancelledException
import ru.aigram.exception.impl.SpeechInputDisabledException
import ru.aigram.speech.SpeechProcessExecutor
import ru.aigram.speech.SpeechWorkerResult
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChatSpeechInputTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `chat opt in transcribes owner and contact voices while global input remains disabled`() {
        val calls = AtomicInteger()
        val process = SpeechProcessExecutor { _, request, _ ->
            calls.incrementAndGet()
            SpeechWorkerResult(text = Files.readString(Path.of(request.input)))
        }
        SpeechHistoryFixture(directory, process).use { fixture ->
            fixture.enable()
            fixture.settings.update { it.copy(voiceInputEnabled = false) }
            fixture.settings.saveRule(
                "42",
                ChatRule(
                    voiceSettingsMode = VoiceSettingsMode.OVERRIDE,
                    voiceInputEnabled = true,
                ),
            )
            val history = listOf(
                fixture.voice(1, "Owner voice", outgoing = true),
                fixture.voice(2, "Contact voice"),
                fixture.voice(3, "Excluded voice").copy(contextExcluded = true),
                fixture.voice(4, "Deleted voice").copy(deleted = true),
            )
            fixture.telegram.beforeMedia = {
                fixture.settings.update { it.copy(voiceRecognitionModel = "unrelated-global-model") }
            }

            val result = fixture.context.prepare(history)

            assertEquals(listOf("Owner voice", "Contact voice"), result.take(2).map { it.text })
            assertEquals(listOf(1, 2), fixture.telegram.mediaReads)
            assertEquals(2, calls.get())
            assertTrue(result.first().outgoing)
            assertTrue(fixture.telegram.historyReads.isEmpty())
        }
    }

    @Test
    fun `chat opt out rejects manual latest voice and memory never downloads disabled audio`() {
        SpeechHistoryFixture(
            directory,
            SpeechProcessExecutor { _, _, _ ->
                throw AssertionError("Disabled speech must not invoke recognition")
            },
        ).use { fixture ->
            fixture.enable()
            fixture.settings.saveRule("42", ChatRule(voiceSettingsMode = VoiceSettingsMode.OVERRIDE))
            val history = listOf(fixture.voice(1, "Owner", outgoing = true), fixture.voice(2, "Contact"))

            assertFailsWith<SpeechInputDisabledException> { fixture.context.prepare(history) }
            assertEquals(history, fixture.context.prepare(history, rejectDisabledVoice = false))
            assertTrue(fixture.telegram.mediaReads.isEmpty())
        }
    }

    @Test
    fun `changing chat recognition opt in during download cancels before inference`() {
        SpeechHistoryFixture(
            directory,
            SpeechProcessExecutor { _, _, _ ->
                throw AssertionError("Changed preferences must cancel before recognition")
            },
        ).use { fixture ->
            fixture.enable()
            fixture.settings.saveRule(
                "42",
                ChatRule(
                    voiceSettingsMode = VoiceSettingsMode.OVERRIDE,
                    voiceInputEnabled = true,
                ),
            )
            fixture.telegram.beforeMedia = {
                fixture.settings.saveRule("42", fixture.settings.rule("42").copy(voiceInputEnabled = false))
            }

            assertFailsWith<ReplyCancelledException> { fixture.context.prepare(listOf(fixture.voice(1, "Contact"))) }
            assertEquals(listOf(1), fixture.telegram.mediaReads)
        }
    }

    @Test
    fun `unsaved preview rule can enable recognition without changing persisted chat or global settings`() {
        SpeechHistoryFixture(
            directory,
            SpeechProcessExecutor { _, request, _ ->
                SpeechWorkerResult(text = Files.readString(Path.of(request.input)))
            },
        ).use { fixture ->
            fixture.enable()
            fixture.settings.update { it.copy(voiceInputEnabled = false) }
            val before = fixture.settings.get()
            val rule = ChatRule(voiceSettingsMode = VoiceSettingsMode.OVERRIDE, voiceInputEnabled = true)

            val result = fixture.context.prepare(listOf(fixture.voice(1, "Preview voice")), rule = rule)

            assertEquals("Preview voice", result.single().text)
            assertEquals(before, fixture.settings.get())
            assertEquals(listOf(1), fixture.telegram.mediaReads)
        }
    }
}
