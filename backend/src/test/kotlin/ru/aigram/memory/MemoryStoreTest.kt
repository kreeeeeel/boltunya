package ru.aigram.memory

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.memory.OwnerMemoryUpdate
import ru.aigram.exception.impl.InvalidMemoryChatException
import ru.aigram.exception.impl.MemoryConflictException
import ru.aigram.resources.ResourceCatalog
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemoryStoreTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `automatic attempt survives stale successful commits rebuilds and restarts without invalidating snapshots`() {
        val store = store()
        store.observe("42", 10)
        store.edit("42", "Manual note", store.read("42").revision)
        val previous = store.state("42") ?: error("Missing state")
        val revision = store.read("42").documentRevision
        val attempted = store.recordAutomaticAttempt(previous, 100000) ?: error("Missing attempt")
        assertEquals(previous.generation, attempted.generation)
        assertEquals(previous.changeVersion, attempted.changeVersion)
        assertTrue(
            store.commit(
                previous,
                revision,
                "Collected fact",
                previous.copy(headId = 10, updatedAt = 100010, recentUpdatedAt = 100010, ownerMemoryVersion = 1),
            ),
        )
        val restored = store()
        assertEquals(100000L, restored.state("42")?.automaticAttemptAt)
        restored.rebuild("42", manual = false)
        val rebuilt = store()
        assertEquals(100000L, rebuilt.state("42")?.automaticAttemptAt)
        assertEquals(100010L, rebuilt.state("42")?.updatedAt)
        assertEquals(100010L, rebuilt.state("42")?.recentUpdatedAt)
        assertEquals("Manual note", rebuilt.read("42").notes)
        assertEquals("", rebuilt.read("42").summary)
    }

    @Test
    fun `stale cancelled and other account jobs cannot record an automatic attempt`() {
        val store = store()
        store.observe("42", 10)
        val previous = store.state("42") ?: error("Missing state")
        store.cancelCollection("42")
        assertEquals(null, store.recordAutomaticAttempt(previous, 100000))
        assertEquals(0L, store.state("42")?.automaticAttemptAt)
        store.activate("200")
        store.observe("42", 10)
        assertEquals(null, store.recordAutomaticAttempt(previous, 100000))
        assertEquals(0L, store.state("42")?.automaticAttemptAt)
    }

    @Test
    fun `global profile preserves manual notes across restarts and isolates account profiles`() {
        val store = store()
        store.observe("42", 10)
        store.edit("", "Мои заметки  \n", store.read("").revision)
        val state = store.state("42") ?: error("state")
        val general = store.read("")
        assertTrue(
            store.commit(
                state,
                store.read("42").documentRevision,
                "Chat",
                state,
                OwnerMemoryUpdate(general, "Любит чай. Пишет коротко."),
            ),
        )
        assertEquals("Мои заметки  \n", store.read("").notes)
        val restored = store()
        assertEquals("Любит чай. Пишет коротко.", restored.read("").summary)
        assertTrue(Files.readString(directory.resolve("memory/MEMORY.md")).contains("Любит чай"))
        restored.activate("200")
        assertEquals("", restored.read("").summary)
        assertEquals("Мои заметки  \n", restored.read("").notes)
        restored.activate("100")
        assertEquals("Любит чай. Пишет коротко.", restored.read("").summary)
    }

    @Test
    fun `concurrent global note edit rejects both generated summaries without advancing cursors`() {
        val store = store()
        store.observe("42", 10)
        val state = store.state("42") ?: error("state")
        val general = store.read("")
        store.edit("", "Предпочитаю кофе", general.revision)
        assertFalse(
            store.commit(
                state,
                store.read("42").documentRevision,
                "Chat",
                state.copy(headId = 10),
                OwnerMemoryUpdate(general, "Любит чай"),
            ),
        )
        assertEquals("Предпочитаю кофе", store.read("").notes)
        assertEquals("", store.read("").summary)
        assertEquals(0L, store.state("42")?.headId)
    }

    @Test
    fun `preserves indentation and whitespace of manual Markdown notes`() {
        val store = store()
        store.observe("42")
        val notes = "    indented code\n\nParagraph with a hard break  \n"
        store.edit("42", notes, store.read("42").revision)
        val state = store.state("42") ?: error("state")
        assertTrue(store.commit(state, store.read("42").documentRevision, "Summary", state))
        assertEquals(notes, store.read("42").notes)
    }
    private fun store() = MemoryStore(directory, ResourceCatalog()).also {
        it.initialize()
        it.activate("100")
    }

    @Test
    fun `persists Markdown and checkpoints across restart without overwriting notes`() {
        val store = store()
        store.observe("42", 10)
        val original = store.read("42")
        store.edit("42", "# Мои заметки\nБез обращений на вы", original.revision)
        val state = store.state("42") ?: error("state")
        val document = store.read("42")
        assertTrue(
            store.commit(
                state,
                document.documentRevision,
                "Собеседник любит чай. [id 10]",
                state.copy(headId = 10, backfillBeforeId = 1),
            ),
        )
        val restored = store()
        assertEquals("# Мои заметки\nБез обращений на вы", restored.read("42").notes)
        assertEquals("Собеседник любит чай. [id 10]", restored.read("42").summary)
        assertEquals(1L, restored.state("42")?.backfillBeforeId)
        assertTrue(Files.exists(directory.resolve("memory/MEMORY.md")))
        assertTrue(Files.exists(directory.resolve("memory/100/42/MEMORY.md")))
    }

    @Test
    fun `protects manual edits and incoming changes while a summary is running`() {
        val store = store()
        store.observe("42", 10)
        val state = store.state("42") ?: error("state")
        val original = store.read("42")
        store.edit("42", "Заметки владельца", original.revision)
        assertFalse(store.commit(state, original.documentRevision, "Old result", state.copy(headId = 10)))
        assertFailsWith<MemoryConflictException> { store.edit("42", "Устаревшая форма", original.revision) }
        val edited = store.read("42")
        store.observe("42", 20)
        assertTrue(store.commit(state, edited.documentRevision, "Summary", state.copy(headId = 10, dirty = false)))
        assertEquals(20L, store.state("42")?.observedId)
        assertTrue(store.state("42")?.dirty == true)
    }

    @Test
    fun `account switch and rebuild cannot receive a stale generated result`() {
        val store = store()
        store.observe("42", 10)
        val state = store.state("42") ?: error("state")
        val original = store.read("42")
        store.rebuild("42")
        assertFalse(store.commit(state, original.documentRevision, "Stale", state))
        store.activate("200")
        store.observe("42", 10)
        assertFalse(store.commit(state, original.documentRevision, "Other owner's facts", state))
        assertEquals("", store.read("42").summary)
        assertTrue(Files.exists(directory.resolve("memory/100/42/MEMORY.md")))
    }

    @Test
    fun `rejects directory traversal and can import a plain manually written Markdown file`() {
        val store = store()
        assertFailsWith<InvalidMemoryChatException> { store.read("../../secret") }
        store.observe("42")
        Files.writeString(directory.resolve("memory/100/42/MEMORY.md"), "# Ручная память\nДружим со школы")
        val document = store.read("42")
        val state = store.state("42") ?: error("state")
        assertTrue(store.commit(state, document.documentRevision, "Новая сводка", state))
        assertEquals("# Ручная память\nДружим со школы", store.read("42").notes)
    }
}
