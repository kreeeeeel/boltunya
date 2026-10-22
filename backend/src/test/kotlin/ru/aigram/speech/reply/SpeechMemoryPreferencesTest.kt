package ru.aigram.speech.reply

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.chat.VoiceSettingsMode
import ru.aigram.memory.MemoryBatchBuilder
import ru.aigram.memory.MemoryProcessor
import ru.aigram.memory.MemoryStore
import ru.aigram.memory.MemorySummarizer
import ru.aigram.memory.OwnerMemorySummarizer
import ru.aigram.resources.ResourceCatalog
import ru.aigram.speech.SpeechProcessExecutor
import ru.aigram.speech.SpeechWorkerResult
import ru.aigram.storage.AutomatedReplyStore
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SpeechMemoryPreferencesTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `speech opt out recognition account and session changes after transcription never commit stale memory`() {
        listOf("opt-out", "recognizer", "account", "session").stream().forEach { change ->
            verifyCommit(change, false) { fixture ->
                when (change) {
                    "opt-out" -> fixture.settings.saveRule(
                        "42",
                        fixture.settings.rule("42").copy(
                            voiceInputEnabled = false,
                        ),
                    )
                    "recognizer" -> fixture.settings.saveRule(
                        "42",
                        fixture.settings.rule("42").copy(
                            voiceRecognitionModel = "changed-recognizer",
                        ),
                    )
                    "account" -> fixture.telegram.owner = "200"
                    else -> fixture.connection.version++
                }
            }
        }
    }

    @Test
    fun `changing only synthesis model and rate does not discard a manual memory collection`() {
        verifyCommit("tts", true) { fixture ->
            fixture.settings.saveRule(
                "42",
                fixture.settings.rule("42").copy(
                    voiceSynthesisModel = "piper-en-amy",
                    voiceSpeechRate = 1.5,
                ),
            )
        }
    }

    private fun verifyCommit(name: String, expected: Boolean, change: (SpeechHistoryFixture) -> Unit) {
        val profile = Files.createDirectories(directory.resolve(name))
        val process = SpeechProcessExecutor { _, request, _ ->
            SpeechWorkerResult(text = Files.readString(Path.of(request.input)))
        }
        SpeechHistoryFixture(profile, process).use { fixture ->
            fixture.enable()
            fixture.settings.update { it.copy(voiceInputEnabled = false) }
            fixture.settings.saveRule(
                "42",
                ChatRule(
                    voiceSettingsMode = VoiceSettingsMode.OVERRIDE,
                    voiceInputEnabled = true,
                ),
            )
            fixture.telegram.messages = listOf(fixture.voice(1, "Synthetic contact facts"))
            val store = MemoryStore(profile, ResourceCatalog())
            store.activate("100")
            store.observe("42", 1, manual = true, request = true)
            store.edit("42", "Manual chat notes", store.read("42").revision)
            store.edit("", "Manual owner notes", store.read("").revision)
            val state = assertNotNull(store.state("42"))
            val chatBefore = store.read("42")
            val ownerBefore = store.read("")
            val processor = MemoryProcessor(
                fixture.telegram,
                store,
                MemoryBatchBuilder(AutomatedReplyStore(profile)),
                MemorySummarizer { _, _, sources ->
                    assertEquals("Synthetic contact facts", sources.single().text)
                    change(fixture)
                    "Synthetic summary"
                },
                Clock.fixed(Instant.ofEpochSecond(1000), ZoneOffset.UTC),
                OwnerMemorySummarizer { _, _, _ ->
                    throw AssertionError("Contact speech must never become owner factual memory")
                },
                speech = fixture.context,
            )

            assertEquals(expected, processor.process(state, "test-model", true))
            assertEquals(listOf(1), fixture.telegram.mediaReads)
            assertEquals(ownerBefore, store.read(""))
            assertEquals("Manual chat notes", store.read("42").notes)
            if (expected) {
                assertEquals("Synthetic summary", store.read("42").summary)
                assertTrue(assertNotNull(store.state("42")).processedMessages > state.processedMessages)
            } else {
                assertEquals(chatBefore, store.read("42"))
                assertEquals(state, store.state("42"))
                assertTrue(store.read("42").summary.isEmpty())
            }
        }
    }
}
