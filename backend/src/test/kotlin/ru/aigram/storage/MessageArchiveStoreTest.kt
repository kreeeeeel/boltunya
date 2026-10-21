package ru.aigram.storage

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.ChatMessage
import ru.aigram.domain.MessageEntity
import ru.aigram.exception.impl.InvalidHistoryIdentifierException
import java.nio.file.Path
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageArchiveStoreTest {
    @TempDir lateinit var directory: Path
    private fun store() = MessageArchiveStore(directory, Clock.systemUTC())
    private fun message(id: Int, text: String = "Текст") =
        ChatMessage((id.toLong() shl 20).toString(), "42", text, false, 100L + id)

    @Test
    fun `retains edited text formatting and permanent deletion across restarts without resurrection`() {
        val store = store()
        val original = message(1).copy(entities = listOf(MessageEntity(0, 5, "bold")))
        store.record("100", listOf(original))
        val edited = original.copy(text = "Новый текст")
        store.record("100", listOf(edited))
        store.delete("100", "42", listOf(original.id.toLong()), true, false)
        store.record("100", listOf(original))
        val saved = store().deleted("100", "42").single()
        assertEquals("Новый текст", saved.text)
        assertEquals(original.entities, saved.entities)
        assertTrue(saved.deleted)
        assertTrue(saved.deletedAt > 0)
        assertTrue(store().deleted("200", "42").isEmpty())
        assertFailsWith<InvalidHistoryIdentifierException> { store.deleted("100", "../../secret") }
    }

    @Test
    fun `cache eviction and temporary unavailability do not mark messages deleted`() {
        val store = store()
        val original = message(1)
        store.record("100", listOf(original))
        store.delete("100", "42", listOf(original.id.toLong()), true, true)
        store.delete("100", "42", listOf(original.id.toLong()), false, false)
        assertTrue(store.deleted("100", "42").isEmpty())
        assertFalse(store.find("100", "42", original.id)?.deleted == true)
    }

    @Test
    fun `merges deleted history into bounded pages without duplicating or skipping the next page`() {
        val store = store()
        val messages = (1..260).map { message(it) }
        store.record("100", messages)
        store.delete("100", "42", listOf(messages.last().id.toLong(), messages.first().id.toLong()), true, false)
        val first = store.merge("100", "42", messages.subList(249, 259), 0, 10)
        assertEquals((251..260).map { message(it).id }, first.map { it.id })
        assertTrue(first.last().deleted)
        val second = store.merge("100", "42", messages.subList(239, 250), first.first().id.toLong(), 10)
        assertEquals((241..250).map { message(it).id }, second.map { it.id })
        assertEquals(1, store.deleted("100", "42", messages.last().id.toLong()).size)
    }

    @Test
    fun `an unknown deletion has no fabricated content but blocks a delayed original from resurrecting`() {
        val store = store()
        val original = message(1)
        store.delete("100", "42", listOf(original.id.toLong()), true, false)
        assertTrue(store.deleted("100", "42").isEmpty())
        store.record("100", listOf(original))
        assertEquals(original.text, store.deleted("100", "42").single().text)
        assertTrue(store.deleted("100", "42").single().deleted)
    }

    @Test
    fun `duplicate permanent updates are idempotent including unknown tombstones`() {
        val store = store()
        val original = message(1)
        assertTrue(store.delete("100", "42", listOf(original.id.toLong()), true, false))
        assertFalse(store.delete("100", "42", listOf(original.id.toLong()), true, false))
        store.record("100", listOf(original))
        val deleted = store.find("100", "42", original.id)
        assertFalse(store.delete("100", "42", listOf(original.id.toLong()), true, false))
        assertEquals(deleted, store.find("100", "42", original.id))
        assertTrue(store.delete("101", "42", listOf(original.id.toLong()), true, false))
    }
}
