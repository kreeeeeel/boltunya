package ru.aigram.storage

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.chat.ChatMessage
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WritingStyleStoreTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `keeps owner samples when recent history has no manual replies and clears on logout`() {
        val store = WritingStyleStore(directory)
        val owner = ChatMessage("1", "42", "ща гляну))", true, 0)
        val incoming = ChatMessage("2", "42", "Текст собеседника", false, 0)
        val automated = ChatMessage("3", "42", "Искусственный стиль", true, 0, automated = true)
        assertEquals(listOf(owner), store.update("42", listOf(owner, incoming, automated)))
        val restored = WritingStyleStore(directory)
        assertEquals(listOf(owner), restored.update("42", listOf(incoming)))
        assertEquals(1, restored.update("42", listOf(owner)).size)
        assertTrue(restored.update("43", listOf(incoming)).isEmpty())
        restored.clear()
        assertTrue(WritingStyleStore(directory).update("42", emptyList()).isEmpty())
    }

    @Test
    fun `new owner text replaces an old sample by identity and editing forgets only the affected message`() {
        val store = WritingStyleStore(directory)
        val first = ChatMessage("1", "42", "Original", true, 10)
        val second = ChatMessage("2", "42", "Second", true, 11)
        val otherChat = ChatMessage("3", "43", "Other chat", true, 12)
        store.update("42", listOf(first, second))
        store.update("43", listOf(otherChat))
        val edited = first.copy(text = "  Edited\n")

        assertEquals(listOf(edited, second), store.update("42", listOf(edited)))
        store.forget("42", setOf("1"))
        val reopened = WritingStyleStore(directory)
        assertEquals(listOf(second), reopened.update("42", emptyList()))
        assertEquals(listOf(otherChat), reopened.update("43", emptyList()))
        assertEquals(listOf(second), reopened.update("42", listOf(edited.copy(automated = true))))
    }
}
