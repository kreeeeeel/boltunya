package ru.aigram.memory

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.ChatMessage
import ru.aigram.exception.impl.MemorySourceContextLimitException
import ru.aigram.storage.AutomatedReplyStore
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MemoryBatchBudgetTest {
    @TempDir lateinit var directory: Path

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
