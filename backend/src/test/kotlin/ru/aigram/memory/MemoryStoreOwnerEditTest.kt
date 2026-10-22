package ru.aigram.memory

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.exception.impl.MemoryConflictException
import ru.aigram.exception.impl.MemoryNotReadyException
import ru.aigram.resources.ResourceCatalog
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MemoryStoreOwnerEditTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `owner is checked after acquiring the edit monitor before a different account can be written`() {
        val store = MemoryStore(directory, ResourceCatalog())
        val baseline = "Identical synthetic notes"
        prepare(store, "101", baseline)
        prepare(store, "100", baseline)
        val revision = store.read("42").revision
        val entered = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val editing = synchronized(store) {
                val future = executor.submit<Unit> {
                    entered.countDown()
                    store.edit("42", "Owner 100 request", revision, "100")
                }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                store.activate("101")
                future
            }

            val failure = assertFailsWith<ExecutionException> { editing.get(5, TimeUnit.SECONDS) }
            assertIs<MemoryNotReadyException>(failure.cause)
            assertEquals(baseline, store.read("42").notes)
            store.activate("100")
            assertEquals(baseline, store.read("42").notes)
            val restored = MemoryStore(directory, ResourceCatalog())
            restored.activate("101")
            assertEquals(baseline, restored.read("42").notes)
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `owner guarded edit preserves summary conflicts persistence and legacy direct callers`() {
        val store = MemoryStore(directory, ResourceCatalog())
        prepare(store, "100", "Initial notes")
        val state = store.state("42") ?: error("Missing synthetic state")
        val previous = store.read("42")
        assertTrue(store.commit(state, previous.documentRevision, "Independent collected facts", state))

        store.edit("42", "Owner guarded notes", previous.revision, "100")
        assertEquals("Independent collected facts", store.read("42").summary)
        assertFailsWith<MemoryConflictException> {
            store.edit("42", "Stale notes", previous.revision, "100")
        }
        val restored = MemoryStore(directory, ResourceCatalog())
        restored.activate("100")
        assertEquals("Owner guarded notes", restored.read("42").notes)
        assertEquals("Independent collected facts", restored.read("42").summary)

        restored.edit("42", "Existing direct caller", restored.read("42").revision)
        assertEquals("Existing direct caller", restored.read("42").notes)
        assertEquals("Independent collected facts", restored.read("42").summary)
    }

    private fun prepare(store: MemoryStore, ownerId: String, notes: String) {
        store.activate(ownerId)
        store.observe("42")
        store.edit("42", notes, store.read("42").revision)
    }
}
