package ru.aigram.storage

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.MessageEntity
import ru.aigram.exception.impl.InvalidHistoryIdentifierException
import java.nio.file.Path
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    @Test
    fun `ordinary history cannot lower the edit timestamp before a permanent deletion`() {
        val store = store()
        val original = message(1).copy(editedAt = 100)
        val edited = original.copy(
            text = "Edited caption",
            entities = listOf(MessageEntity(0, 6, "italic")),
            editedAt = 101,
        )
        store.record("100", listOf(original))
        store.recordEdited("100", listOf(edited))

        store.record("100", listOf(original))
        val page = store.recordPage("100", "42", listOf(original), store.version("100", "42"))
        assertEquals(listOf(edited), page)
        assertEquals(edited, store.find("100", "42", original.id))

        store.delete("100", "42", listOf(original.id.toLong()), permanent = true, fromCache = false)
        val deleted = store().deleted("100", "42").single()
        assertEquals(edited.text, deleted.text)
        assertEquals(edited.entities, deleted.entities)
        assertEquals(edited.editedAt, deleted.editedAt)
    }

    @Test
    fun `same second edits survive nested late native pages and the deletion snapshot`() {
        val store = store()
        val original = message(1).copy(editedAt = 100)
        val edited = original.copy(text = "New text in the same second", entities = listOf(MessageEntity(0, 3, "bold")))
        store.record("100", listOf(original))
        val readerVersion = store.version("100", "42")
        val componentVersion = store.version("100", "42")
        val deletionVersion = store.version("100", "42")

        store.recordEdited("100", listOf(edited))
        assertEquals(listOf(edited), store.recordPage("100", "42", listOf(original), readerVersion))
        assertEquals(listOf(edited), store.recordPage("100", "42", listOf(original), componentVersion))
        assertEquals(listOf(edited), store.recordPage("100", "42", listOf(original), deletionVersion))

        store.delete("100", "42", listOf(original.id.toLong()), permanent = true, fromCache = false)
        val deleted = store().deleted("100", "42").single()
        assertEquals(edited.text, deleted.text)
        assertEquals(edited.entities, deleted.entities)
        assertEquals(edited.editedAt, deleted.editedAt)
    }

    @Test
    fun `a stale page cannot persist unknown entries until a fresh snapshot reads them`() {
        val store = store()
        val original = message(1)
        val unknown = message(2)
        store.record("100", listOf(original))
        val captured = store.version("100", "42")
        val edited = original.copy(text = "Edited")
        store.recordEdited("100", listOf(edited))

        val stale = store.recordPage("100", "42", listOf(original, unknown), captured)
        assertEquals(listOf(edited, unknown), stale)
        assertNull(store.find("100", "42", unknown.id))

        val fresh = store.recordPage("100", "42", listOf(edited, unknown), store.version("100", "42"))
        assertEquals(listOf(edited, unknown), fresh)
        assertEquals(unknown, store().find("100", "42", unknown.id))
    }

    @Test
    fun `snapshot versions are scoped by account and chat without changing persisted messages`() {
        val store = store()
        val original = message(1)
        store.record("100", listOf(original))
        val otherAccountVersion = store.version("200", "42")
        val otherChatVersion = store.version("100", "43")
        store.recordEdited("100", listOf(original.copy(text = "Edited")))

        assertEquals(1L, store.version("100", "42"))
        assertEquals(0L, otherAccountVersion)
        assertEquals(0L, otherChatVersion)
        assertEquals(listOf(original), store.recordPage("200", "42", listOf(original), otherAccountVersion))
        val otherChat = original.copy(chatId = "43")
        assertEquals(listOf(otherChat), store.recordPage("100", "43", listOf(otherChat), otherChatVersion))
        assertEquals(original, store().find("200", "42", original.id))
        assertEquals(otherChat, store().find("100", "43", original.id))
        assertEquals("Edited", store().find("100", "42", original.id)?.text)
    }
}
