package ru.aigram.memory

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.exception.impl.MemorySourceContextLimitException
import ru.aigram.storage.AutomatedReplyStore
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.IntStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MemoryBatchBudgetTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `finds the largest fitting suffix with logarithmic budget evaluations`() {
        val checks = AtomicInteger()
        val messages = IntStream.rangeClosed(1, 2048).mapToObj { id ->
            ChatMessage("$id", "chat", "Fact $id", false, id.toLong())
        }.toList()
        val batch = MemoryBatchBuilder(AutomatedReplyStore(directory)).select(messages) { sources ->
            checks.incrementAndGet()
            sources.size <= 777
        }

        assertEquals(777, batch.sources.size)
        assertEquals("1272", batch.messages.first().id)
        assertEquals("2048", batch.messages.last().id)
        assertTrue(checks.get() <= 14, "Budget checks must not grow linearly with dialog size")
    }

    @Test
    fun `retains excluded cursor gaps before the first fitting source`() {
        val messages = listOf(
            ChatMessage("1", "chat", "Too old", false, 1),
            ChatMessage("2", "chat", "Deleted", false, 2, deleted = true),
            ChatMessage("3", "chat", "Excluded", false, 3, contextExcluded = true),
            ChatMessage("4", "chat", "Fitting", false, 4),
        )
        val batch = MemoryBatchBuilder(AutomatedReplyStore(directory)).select(messages) { it.size <= 1 }

        assertEquals(listOf("2", "3", "4"), batch.messages.map { it.id })
        assertEquals(listOf("4"), batch.sources.map { it.id })
    }

    @Test
    fun `model capacity determines batch size without a fixed message count`() {
        val builder = MemoryBatchBuilder(AutomatedReplyStore(directory))
        val history = (1..60).map { ChatMessage("$it", "chat", "Fact ".repeat(100), false, it.toLong()) }
        val small = builder.select(history) { sources -> sources.sumOf { it.text.length } <= 2000 }
        val large = builder.select(history) { sources -> sources.sumOf { it.text.length } <= 16000 }
        assertEquals(4, small.sources.size)
        assertEquals(32, large.sources.size)
        assertEquals("60", small.messages.last().id)
        assertEquals("29", large.messages.first().id)
    }

    @Test
    fun `oversized single source is never silently shortened and skipped`() {
        val message = ChatMessage("8", "chat", "a".repeat(10000) + "Important final fact", true, 1)
        assertFailsWith<MemorySourceContextLimitException> {
            MemoryBatchBuilder(AutomatedReplyStore(directory)).select(listOf(message)) { sources ->
                sources.sumOf { it.text.length } <= 3000
            }
        }
        assertEquals("Important final fact", message.text.takeLast(20))
    }

    @Test
    fun `impossible instruction and existing memory budget fails without skipping source messages`() {
        assertFailsWith<MemorySourceContextLimitException> {
            MemoryBatchBuilder(AutomatedReplyStore(directory)).select(
                listOf(ChatMessage("1", "chat", "Keep this fact", false, 1)),
            ) { false }
        }
    }
}
