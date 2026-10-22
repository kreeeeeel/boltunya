package ru.aigram.speech.reply

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.chat.VoiceSettingsMode
import ru.aigram.domain.settings.VoiceReplyMode
import ru.aigram.exception.impl.ReplyCancelledException
import ru.aigram.speech.SpeechProcessExecutor
import ru.aigram.speech.SpeechWorkerRequest
import ru.aigram.speech.SpeechWorkerResult
import ru.aigram.speech.installSpeechFixture
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChatVoiceReplyTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `mirror uses the chat voice and rate while unrelated global voice changes do not cancel synthesis`() {
        val seen = AtomicReference<SpeechWorkerRequest>()
        val change = AtomicReference<() -> Unit>({})
        val process = SpeechProcessExecutor { _, request, _ ->
            seen.set(request)
            change.get().invoke()
            Files.writeString(Path.of(request.output), "Synthetic wave")
            SpeechWorkerResult(sampleRate = 22050, samples = 44100)
        }
        SpeechHistoryFixture(directory, process).use { fixture ->
            listOf("sherpa-runtime", "piper-en-amy").stream().forEach { installSpeechFixture(directory, it) }
            val rule = ChatRule(
                voiceSettingsMode = VoiceSettingsMode.OVERRIDE,
                voiceInputEnabled = true,
                voiceReplyMode = VoiceReplyMode.MIRROR,
                voiceSynthesisModel = "piper-en-amy",
                voiceSpeechRate = 1.3,
            )
            fixture.settings.saveRule("42", rule)
            val preferences = fixture.settings.get().voicePreferences(rule)
            val policy = VoiceReplyPolicy()
            assertTrue(policy.wantsVoice(preferences, "voice"))
            val store = VoiceDraftStore(directory, fixture.telegram, VoiceTestClock())
            val service = VoiceReplyService(
                fixture.engine,
                fixture.catalog,
                fixture.codec,
                store,
                fixture.settings,
                fixture.telegram,
                fixture.tracing,
            )
            change.set {
                fixture.settings.update { it.copy(voiceSynthesisModel = "piper-ru-dmitri", voiceSpeechRate = 0.5) }
            }

            val result = service.compose(ReplyResult("Exact answer", 0, 0.0), "42", "100", preferences, rule)

            assertNotNull(result.voice)
            assertTrue(seen.get().modelDirectory.endsWith("piper-en-amy"))
            assertEquals(1.3f, seen.get().speed)
            assertEquals("Exact answer", seen.get().text)
            assertTrue(fixture.telegram.delegate.sentVoices.isEmpty())

            change.set {
                fixture.settings.saveRule("42", rule.copy(voiceSpeechRate = 0.75))
            }
            assertFailsWith<ReplyCancelledException> {
                service.compose(ReplyResult("Stale answer", 0, 0.0), "42", "100", preferences, rule)
            }
        }
    }
}
