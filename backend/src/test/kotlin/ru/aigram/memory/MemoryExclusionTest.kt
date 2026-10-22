package ru.aigram.memory

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ContextExclusionScope
import ru.aigram.domain.chat.ContextExclusionUpdate
import ru.aigram.resources.ResourceCatalog
import ru.aigram.storage.AutomatedReplyStore
import ru.aigram.storage.ContextExclusionStore
import java.nio.file.Path
import java.time.Clock
import java.util.stream.IntStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemoryExclusionTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `deleted archive entries remain cursors without returning their facts to rebuilt memory`() {
        val messages = listOf(
            ChatMessage("1", "42", "Deleted owner fact", true, 1, deleted = true),
            ChatMessage("2", "42", "Retained fact", false, 2),
        )
        val batch = MemoryBatchBuilder(AutomatedReplyStore(directory)).select(messages)
        assertEquals(messages, batch.messages)
        assertEquals(listOf("Retained fact"), batch.sources.map { it.text })
    }

    @Test
    fun `keeps excluded messages as pagination cursors while excluding every summary source`() {
        val messages = IntStream.rangeClosed(1, 100).mapToObj { id ->
            ChatMessage("$id", "42", "Excluded fact", true, id.toLong(), contextExcluded = true)
        }.toList()
        val batch = MemoryBatchBuilder(AutomatedReplyStore(directory)).select(messages)
        assertEquals(messages, batch.messages)
        assertTrue(batch.sources.isEmpty())
        val mixed = MemoryBatchBuilder(AutomatedReplyStore(directory)).select(
            messages + ChatMessage("101", "42", "Included fact", false, 101),
        )
        assertEquals(101, mixed.messages.size)
        assertEquals(listOf("101"), mixed.sources.map { it.id })
    }

    @Test
    fun `walks entirely excluded pages without summarizing them or ending deep history early`() {
        val telegram = MemoryTelegramGateway()
        telegram.messages = IntStream.rangeClosed(1, 250).mapToObj { id ->
            ChatMessage("$id", "42", "Excluded fact", true, id.toLong())
        }.toList()
        val exclusions = ContextExclusionStore(directory)
        exclusions.update("100", "42", "250", ContextExclusionUpdate(ContextExclusionScope.THROUGH))
        val store = MemoryStore(directory, ResourceCatalog())
        store.activate("100")
        store.observe("42", 250)
        val processor = MemoryProcessor(
            telegram,
            store,
            MemoryBatchBuilder(AutomatedReplyStore(directory)),
            MemorySummarizer { _, _, _ -> error("Excluded messages must never enter chat memory") },
            Clock.systemUTC(),
            OwnerMemorySummarizer { _, _, _ -> error("Excluded messages must never enter owner memory") },
            exclusions,
        )
        assertTrue(processor.process(store.state("42") ?: error("state"), "test", true))
        assertTrue(store.state("42")?.backfillComplete == true)
        assertEquals(250L, store.state("42")?.processedMessages)
        assertEquals(listOf(0L, 151L, 51L), telegram.reads.map { it.second })
        assertEquals("", store.read("42").facts())
        assertEquals("", store.read("").facts())
    }

    @Test
    fun `discards both memory updates if context policy changes during either generation`() {
        listOf("owner", "chat").stream().forEach { stage ->
            val location = directory.resolve(stage)
            val exclusions = ContextExclusionStore(location)
            val telegram = MemoryTelegramGateway()
            telegram.messages = listOf(ChatMessage("1", "42", "A private fact", true, 1))
            val store = MemoryStore(location, ResourceCatalog())
            store.activate("100")
            store.observe("42", 1)
            val state = store.state("42") ?: error("state")
            val processor = MemoryProcessor(
                telegram,
                store,
                MemoryBatchBuilder(AutomatedReplyStore(location)),
                MemorySummarizer { _, _, _ ->
                    if (stage == "chat") {
                        exclusions.update("100", "42", "1", ContextExclusionUpdate())
                    }
                    "A private fact"
                },
                Clock.systemUTC(),
                OwnerMemorySummarizer { _, _, _ ->
                    if (stage == "owner") {
                        exclusions.update("100", "42", "1", ContextExclusionUpdate())
                    }
                    "A private fact"
                },
                exclusions,
            )
            assertFalse(processor.process(state, "test", true), stage)
            assertEquals(state, store.state("42"))
            assertEquals("", store.read("42").facts())
            assertEquals("", store.read("").facts())
        }
    }
}
