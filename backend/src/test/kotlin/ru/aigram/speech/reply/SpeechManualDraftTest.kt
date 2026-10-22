package ru.aigram.speech.reply

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.automation.ReplyDeliveryPolicy
import ru.aigram.component.speech.SpeechReplyComponent
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.chat.VoiceSettingsMode
import ru.aigram.exception.impl.InvalidSpeechAssetException
import ru.aigram.exception.impl.InvalidSpeechRequestException
import ru.aigram.exception.impl.SpeechDraftUnavailableException
import ru.aigram.speech.SpeechProcessExecutor
import ru.aigram.speech.SpeechWorkerRequest
import ru.aigram.speech.SpeechWorkerResult
import ru.aigram.speech.awaitSpeech
import ru.aigram.speech.installSpeechFixture
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SpeechManualDraftTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `manual voice captures unsaved model and rate without changing settings or invoking a conversation model`() {
        val seen = AtomicReference<SpeechWorkerRequest>()
        val process = SpeechProcessExecutor { _, request, _ ->
            seen.set(request)
            Files.writeString(Path.of(request.output), "Synthetic wave")
            SpeechWorkerResult(sampleRate = 22050, samples = 44100)
        }
        SpeechHistoryFixture(directory, process).use { fixture ->
            listOf("sherpa-runtime", "piper-en-amy", "piper-ru-irina", "piper-ru-dmitri").stream().forEach {
                installSpeechFixture(directory, it)
            }
            fixture.settings.saveRule(
                "42",
                ChatRule(
                    voiceSettingsMode = VoiceSettingsMode.OVERRIDE,
                    voiceSynthesisModel = "piper-ru-irina",
                    voiceSpeechRate = 1.25,
                ),
            )
            val drafts = VoiceDraftStore(directory, fixture.telegram, VoiceTestClock())
            val voices = VoiceReplyService(
                fixture.engine,
                fixture.catalog,
                fixture.codec,
                drafts,
                fixture.settings,
                fixture.telegram,
                fixture.tracing,
            )
            ReplyCoordinator(fixture.settings, fixture.telegram, tracing = fixture.tracing) { _, _ ->
                throw AssertionError("Manual TTS must not invoke the conversation model")
            }.use { queue ->
                val component = SpeechReplyComponent(
                    fixture.telegram,
                    fixture.settings,
                    queue,
                    ReplyDeliveryPolicy(fixture.settings),
                    voices,
                    drafts,
                    fixture.catalog,
                )
                queue.pauseQueue(true)
                val before = fixture.settings.get()
                val job = component.enqueue("42", "  Exact manual speech.  ", "piper-en-amy", 1.5)
                assertEquals(before, fixture.settings.get())
                fixture.settings.update { it.copy(voiceSynthesisModel = "piper-ru-dmitri", voiceSpeechRate = 0.75) }
                queue.pauseQueue(false)
                awaitSpeech { queue.draft(job.id).status == "ready" }

                val result = queue.draft(job.id).result
                assertNotNull(result)
                val voice = result.voice
                assertNotNull(voice)
                assertEquals("  Exact manual speech.  ", seen.get().text)
                assertEquals("piper-en-amy", result.model)
                assertTrue(seen.get().modelDirectory.endsWith("piper-en-amy"))
                assertEquals(1.5f, seen.get().speed)
                assertEquals(0.75, fixture.settings.get().voiceSpeechRate)
                assertTrue(fixture.telegram.delegate.sentVoices.isEmpty())
                assertFailsWith<SpeechDraftUnavailableException> { drafts.send("43", voice.id, "1", 0, false) }
                fixture.telegram.owner = "200"
                assertFailsWith<SpeechDraftUnavailableException> { drafts.audio(voice.id) }
                fixture.telegram.owner = "100"
                drafts.send("42", voice.id, "1", 0, false)
                assertEquals(1, fixture.telegram.delegate.sentVoices.size)
                assertEquals(listOf(false), fixture.telegram.delegate.voiceAutomated)
                assertTrue(fixture.telegram.delegate.sent.isEmpty())

                assertFailsWith<InvalidSpeechRequestException> { component.enqueue("42", "Text", speed = 2.1) }
                assertFailsWith<InvalidSpeechAssetException> {
                    component.enqueue("42", "Text", modelId = "whisper-tiny")
                }
                val fallback = component.enqueue("42", "Chat captured settings")
                awaitSpeech { queue.draft(fallback.id).status == "ready" }
                assertTrue(seen.get().modelDirectory.endsWith("piper-ru-irina"))
                assertEquals(1.25f, seen.get().speed)
                fixture.settings.saveRule(
                    "42",
                    fixture.settings.rule("42").copy(
                        voiceSettingsMode = VoiceSettingsMode.INHERIT,
                    ),
                )
                val inherited = component.enqueue("42", "Global captured settings")
                awaitSpeech { queue.draft(inherited.id).status == "ready" }
                assertTrue(seen.get().modelDirectory.endsWith("piper-ru-dmitri"))
                assertEquals(0.75f, seen.get().speed)
                queue.pauseQueue(true)
                val cancelled = component.enqueue("42", "Cancelled before synthesis", "piper-en-amy", 1.0)
                queue.cancel(cancelled.id)
                queue.pauseQueue(false)
                awaitSpeech { queue.draft(cancelled.id).status == "cancelled" }
                assertEquals("Global captured settings", seen.get().text)
            }
        }
    }
}
