package ru.aigram.speech.reply

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.exception.impl.ReplyCancelledException
import ru.aigram.exception.impl.SpeechAudioLimitException
import ru.aigram.exception.impl.SpeechNotInstalledException
import ru.aigram.speech.SpeechProcessExecutor
import ru.aigram.speech.SpeechWorkerResult
import ru.aigram.storage.WritingStyleStore
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SpeechHistoryContextTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `all selected incoming owner and automated voices are transcribed without changing authorship`() {
        val calls = AtomicInteger()
        SpeechHistoryFixture(directory, process(calls)).use { fixture ->
            fixture.enable()
            val history = listOf(
                fixture.voice(1, "Contact speech"),
                fixture.voice(2, "Owner speech", outgoing = true),
                fixture.voice(3, "Automated speech", outgoing = true, automated = true),
                fixture.voice(4, "Deleted speech").copy(deleted = true),
                fixture.voice(5, "Excluded speech").copy(contextExcluded = true),
                ChatMessage("6", "42", "Latest text", false, 6),
            )

            val result = fixture.context.prepare(history)

            assertEquals(listOf("Contact speech", "Owner speech", "Automated speech"), result.take(3).map { it.text })
            assertEquals(listOf(1, 2, 3), fixture.telegram.mediaReads)
            assertTrue(result[1].outgoing)
            assertTrue(result[2].automated)
            assertTrue(WritingStyleStore(directory).update("42", result).isEmpty())
            assertEquals(history[3], result[3])
            assertEquals(history[4], result[4])
            assertEquals(history[5], result[5])
            assertEquals(result, fixture.context.prepare(history))
            assertEquals(3, calls.get())
            assertTrue(fixture.telegram.historyReads.isEmpty())
            assertTrue(fixture.telegram.delegate.sent.isEmpty())
        }
    }

    @Test
    fun `cached transcripts respect edits file identity account and connection version`() {
        val calls = AtomicInteger()
        SpeechHistoryFixture(directory, process(calls)).use { fixture ->
            fixture.enable()
            val original = fixture.voice(1, "First speech", outgoing = true)
            fixture.context.prepare(listOf(original))
            val edited = original.copy(editedAt = 10)
            fixture.context.prepare(listOf(edited))
            val replaced = edited.copy(media = edited.media?.copy(uniqueId = "replaced-file"))
            fixture.context.prepare(listOf(replaced))
            fixture.connection.version++
            fixture.context.prepare(listOf(replaced))
            fixture.telegram.owner = "200"
            fixture.context.prepare(listOf(replaced))

            assertEquals(5, calls.get())
            val deleted = replaced.copy(deleted = true)
            assertEquals(deleted, fixture.context.prepare(listOf(deleted)).single())
            assertEquals(5, calls.get())
        }
    }

    @Test
    fun `account and session changes stop between files without returning partial history`() {
        listOf("account", "session", "disabled").stream().forEach { change ->
            val profile = Files.createDirectories(directory.resolve(change))
            val calls = AtomicInteger()
            SpeechHistoryFixture(profile, process(calls)).use { fixture ->
                fixture.enable()
                val history = listOf(fixture.voice(1, "First"), fixture.voice(2, "Second"))
                fixture.telegram.beforeMedia = {
                    when (change) {
                        "account" -> fixture.telegram.owner = "200"
                        "session" -> fixture.connection.version++
                        else -> fixture.settings.update { it.copy(voiceInputEnabled = false) }
                    }
                }

                assertFailsWith<ReplyCancelledException> { fixture.context.prepare(history) }
                assertEquals(listOf(1), fixture.telegram.mediaReads)
                assertEquals(0, calls.get())
            }
        }
    }

    @Test
    fun `cancellation audio limits and missing recognition never fabricate a transcript`() {
        val calls = AtomicInteger()
        SpeechHistoryFixture(directory, process(calls)).use { fixture ->
            fixture.settings.update { it.copy(voiceInputEnabled = true) }
            val voice = fixture.voice(1, "Exact speech")
            assertFailsWith<SpeechNotInstalledException> { fixture.context.prepare(listOf(voice)) }
            fixture.enable()
            assertFailsWith<SpeechAudioLimitException> {
                fixture.context.prepare(listOf(voice.copy(media = voice.media?.copy(duration = 601))))
            }
            try {
                Thread.currentThread().interrupt()
                assertFailsWith<ReplyCancelledException> { fixture.context.prepare(listOf(voice)) }
            } finally {
                Thread.interrupted()
            }
            assertEquals(0, calls.get())
            assertTrue(fixture.telegram.delegate.sentVoices.isEmpty())
            Files.list(directory.resolve("speech/recognition")).use { assertEquals(0L, it.count()) }
        }
    }

    private fun process(calls: AtomicInteger): SpeechProcessExecutor = SpeechProcessExecutor { _, request, _ ->
        calls.incrementAndGet()
        SpeechWorkerResult(text = Files.readString(Path.of(request.input)))
    }
}
