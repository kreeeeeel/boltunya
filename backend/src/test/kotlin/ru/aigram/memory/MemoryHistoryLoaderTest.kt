package ru.aigram.memory

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.memory.ChatMemoryState
import ru.aigram.exception.impl.ReplyCancelledException
import ru.aigram.storage.AutomatedReplyStore
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.IntStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemoryHistoryLoaderTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `fills the model budget across native pages without scanning other chats or losing older messages`() {
        val telegram = telegram(450)
        val batches = MemoryBatchBuilder(AutomatedReplyStore(directory))
        val loader = MemoryHistoryLoader(telegram, batches)
        val state = ChatMemoryState("42", "100")
        val first = loader.load(state, 0, Long.MAX_VALUE, false, { it.size <= 240 }, { true })
        val selected = batches.select(first.messages) { it.size <= 240 }

        assertEquals(300, first.messages.size)
        assertEquals("211", selected.messages.first().id)
        assertEquals("450", selected.messages.last().id)
        assertFalse(first.exhausted)
        assertEquals(listOf("42" to 0L, "42" to 351L, "42" to 251L), telegram.reads)

        val remaining = loader.load(state, 211, Long.MAX_VALUE, false, { it.size <= 240 }, { true })
        assertTrue(remaining.exhausted)
        assertEquals(210, remaining.messages.size)
        assertEquals("1", remaining.messages.first().id)
        assertEquals("210", remaining.messages.last().id)
        assertEquals(450, remaining.messages.size + selected.messages.size)
    }

    @Test
    fun `large model capacity consumes the complete selected dialog in one work unit`() {
        val telegram = telegram(450)
        val loader = MemoryHistoryLoader(telegram, MemoryBatchBuilder(AutomatedReplyStore(directory)))
        val page = loader.load(ChatMemoryState("42", "100"), 0, Long.MAX_VALUE, false, { true }, { true })

        assertTrue(page.exhausted)
        assertEquals(450, page.messages.size)
        assertEquals(listOf(0L, 351L, 251L, 151L, 51L), telegram.reads.map { it.second })
        assertTrue(telegram.reads.stream().allMatch { it.first == "42" })
    }

    @Test
    fun `cancellation between pages prevents the next native history fetch`() {
        val telegram = telegram(250)
        val checks = AtomicInteger()
        val loader = MemoryHistoryLoader(telegram, MemoryBatchBuilder(AutomatedReplyStore(directory)))

        assertFailsWith<ReplyCancelledException> {
            loader.load(ChatMemoryState("42", "100"), 0, Long.MAX_VALUE, false, { true }) {
                checks.incrementAndGet() == 1
            }
        }
        assertEquals(listOf("42" to 0L), telegram.reads)
    }

    private fun telegram(count: Int): MemoryTelegramGateway = MemoryTelegramGateway().also { gateway ->
        gateway.messages = IntStream.rangeClosed(1, count).mapToObj { id ->
            ChatMessage("$id", "42", "Synthetic fact $id", false, id.toLong())
        }.toList() + ChatMessage("999", "43", "Unrelated dialog", false, 999)
    }
}
