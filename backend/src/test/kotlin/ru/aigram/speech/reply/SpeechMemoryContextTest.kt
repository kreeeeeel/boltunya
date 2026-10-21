package ru.aigram.speech.reply

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.memory.MemorySource
import ru.aigram.memory.MemoryBatchBuilder
import ru.aigram.memory.MemoryProcessor
import ru.aigram.memory.MemoryStore
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
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpeechMemoryContextTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `long transcripts narrow the selected batch without skipping history or treating automated speech as owner`() {
        val process = SpeechProcessExecutor { _, request, _ ->
            SpeechWorkerResult(text = Files.readString(Path.of(request.input)))
        }
        SpeechHistoryFixture(directory, process).use { fixture ->
            fixture.enable()
            fixture.telegram.messages = listOf(
                fixture.voice(1, "Owner speech".padEnd(40, '.'), outgoing = true),
                fixture.voice(2, "Contact speech".padEnd(40, '.')),
                fixture.voice(3, "AI speech".padEnd(40, '.'), outgoing = true, automated = true),
                fixture.voice(4, "Deleted speech").copy(deleted = true),
                fixture.voice(5, "Excluded speech").copy(contextExcluded = true),
            )
            val store = MemoryStore(directory, ResourceCatalog())
            store.activate("100")
            store.observe("42", 5)
            val summarizer = SpeechBoundedMemorySummarizer(60)
            val owner = mutableListOf<MemorySource>()
            val processor = MemoryProcessor(
                fixture.telegram,
                store,
                MemoryBatchBuilder(AutomatedReplyStore(directory)),
                summarizer,
                Clock.fixed(Instant.ofEpochSecond(1000), ZoneOffset.UTC),
                OwnerMemorySummarizer { _, _, sources ->
                    owner.addAll(sources)
                    "Owner confirmed facts"
                },
                speech = fixture.context,
            )

            Stream.of(true, false, false, false).forEach { recent ->
                val state = store.state("42")
                assertTrue(state != null)
                assertTrue(processor.process(state, "test-model", recent))
            }

            assertEquals(listOf("3", "2", "1"), summarizer.seen.map { it.id })
            assertTrue(summarizer.seen.all { it.text.length == 40 })
            assertEquals(listOf("1"), owner.map { it.id })
            assertEquals(listOf(1, 2, 3), fixture.telegram.mediaReads)
            assertEquals(true, store.state("42")?.backfillComplete)
            assertTrue(fixture.telegram.delegate.sent.isEmpty())
        }
    }

    @Test
    fun `memory collection never enables speech recognition without the explicit setting`() {
        SpeechHistoryFixture(
            directory,
            SpeechProcessExecutor { _, _, _ ->
                throw AssertionError("Disabled recognition must not invoke its worker")
            },
        ).use { fixture ->
            fixture.telegram.messages = listOf(fixture.voice(1, "Must not be read", outgoing = true))
            val store = MemoryStore(directory, ResourceCatalog())
            store.activate("100")
            store.observe("42", 1)
            val summarizer = SpeechBoundedMemorySummarizer(60)
            val processor = MemoryProcessor(
                fixture.telegram,
                store,
                MemoryBatchBuilder(AutomatedReplyStore(directory)),
                summarizer,
                Clock.fixed(Instant.ofEpochSecond(1000), ZoneOffset.UTC),
                OwnerMemorySummarizer { _, existing, _ -> existing },
                speech = fixture.context,
            )
            val state = store.state("42")
            assertTrue(state != null)

            assertTrue(processor.process(state, "test-model", true))

            assertEquals("Voice placeholder", summarizer.seen.single().text)
            assertTrue(fixture.telegram.mediaReads.isEmpty())
        }
    }
}
