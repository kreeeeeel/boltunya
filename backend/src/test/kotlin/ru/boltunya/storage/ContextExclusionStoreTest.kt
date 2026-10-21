package ru.boltunya.storage

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ContextExclusionScope
import ru.boltunya.domain.ContextExclusionUpdate
import ru.boltunya.exception.impl.InvalidContextSelectionException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContextExclusionStoreTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `persists account-scoped ordered exclusion and restoration ranges`() {
        val store = ContextExclusionStore(directory)
        val messages = (1..7).map { ChatMessage("$it", "42", "Original message $it", false, it.toLong()) }
        store.update("100", "42", "4", ContextExclusionUpdate(ContextExclusionScope.THROUGH))
        store.update("100", "42", "6", ContextExclusionUpdate(ContextExclusionScope.FROM))
        assertEquals(
            listOf("1", "2", "3", "4", "6", "7"),
            store.decorate("100", messages).filter {
                it.contextExcluded
            }.map { it.id },
        )
        store.update("100", "42", "2", ContextExclusionUpdate(ContextExclusionScope.THROUGH, false))
        store.update("100", "42", "7", ContextExclusionUpdate(ContextExclusionScope.FROM, false))
        store.update("100", "42", "3", ContextExclusionUpdate(ContextExclusionScope.MESSAGE, false))
        store.update("100", "42", "7", ContextExclusionUpdate())
        val restored = ContextExclusionStore(directory)
        val decorated = restored.decorate("100", messages)
        assertEquals(listOf("4", "6", "7"), decorated.filter { it.contextExcluded }.map { it.id })
        assertEquals(messages.map { it.text }, decorated.map { it.text })
        assertEquals(6L, restored.revision("100", "42"))
        assertTrue(restored.decorate("101", messages).none { it.contextExcluded })
        assertTrue(restored.decorate("100", messages.map { it.copy(chatId = "43") }).none { it.contextExcluded })
        restored.clear("100", "42")
        assertEquals(7L, restored.revision("100", "42"))
        assertTrue(ContextExclusionStore(directory).decorate("100", messages).none { it.contextExcluded })
    }

    @Test
    fun `strips excluded messages and quoted reply text only for inference`() {
        val store = ContextExclusionStore(directory)
        store.update("100", "42", "1", ContextExclusionUpdate())
        val messages = listOf(
            ChatMessage("1", "42", "Excluded secret", false, 1),
            ChatMessage("2", "42", "Included reply", true, 2, replyToId = "1", replyText = "Excluded secret"),
        )
        assertEquals(2, store.decorate("100", messages).size)
        val inference = store.forInference("100", messages)
        assertEquals(listOf("2"), inference.map { it.id })
        assertEquals("", inference.single().replyText)
        assertFalse(inference.single().contextExcluded)
        assertEquals("Excluded secret", messages[1].replyText)
    }

    @Test
    fun `rejects noncanonical identifiers without creating untrusted files`() {
        val store = ContextExclusionStore(directory)
        listOf("0", "+1", "01", "../outside", "999999999999999999999").stream().forEach { id ->
            assertFailsWith<InvalidContextSelectionException> {
                store.update("100", "42", id, ContextExclusionUpdate())
            }
        }
        assertFailsWith<InvalidContextSelectionException> {
            store.update("../outside", "42", "1", ContextExclusionUpdate())
        }
        assertFailsWith<InvalidContextSelectionException> { store.clear("100", "../outside") }
        assertFalse(Files.exists(directory.resolve("context-exclusions")))
    }
}
