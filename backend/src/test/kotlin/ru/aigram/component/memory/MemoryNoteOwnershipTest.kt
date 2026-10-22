package ru.aigram.component.memory

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.memory.MemoryDocument
import ru.aigram.exception.impl.MemoryConflictException
import ru.aigram.exception.impl.MemoryNotReadyException
import ru.aigram.exception.impl.ValidationFailedException
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MemoryNoteOwnershipTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `account change during validation cannot retarget notes to the newly activated account`() {
        MemoryNoteOwnershipFixture(directory).use { fixture ->
            val baseline = "Synthetic shared notes"
            fixture.prepare("101", baseline)
            fixture.prepare("100", baseline)
            val previous = fixture.component.get("42")
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            fixture.beforeValidation = {
                entered.countDown()
                assertTrue(release.await(5, TimeUnit.SECONDS))
            }
            val executor = Executors.newSingleThreadExecutor()
            try {
                val editing = executor.submit<MemoryDocument> {
                    fixture.component.edit("42", "Submitted for owner 100", previous.revision)
                }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                fixture.ownerId = "101"
                assertEquals("memory/101/42/MEMORY.md", fixture.component.get("42").relativePath)
                release.countDown()

                val failure = assertFailsWith<ExecutionException> { editing.get(5, TimeUnit.SECONDS) }
                assertIs<MemoryNotReadyException>(failure.cause)
                assertEquals(baseline, fixture.store.read("42").notes)
                fixture.store.activate("100")
                assertEquals(baseline, fixture.store.read("42").notes)
            } finally {
                release.countDown()
                executor.shutdownNow()
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            }
        }
    }

    @Test
    fun `account change while preparing saved document rejects the old owner's response`() {
        MemoryNoteOwnershipFixture(directory).use { fixture ->
            fixture.prepare("101", "Other account notes")
            fixture.prepare("100", "Original notes")
            val previous = fixture.component.get("42")
            val statuses = AtomicInteger()
            fixture.afterStatus = {
                if (statuses.incrementAndGet() == 3) {
                    fixture.ownerId = "101"
                }
            }

            assertFailsWith<MemoryNotReadyException> {
                fixture.component.edit("42", "Saved before the account changed", previous.revision)
            }
            assertEquals(4, statuses.get())
            assertEquals("Saved before the account changed", fixture.store.read("42").notes)
            fixture.store.activate("101")
            assertEquals("Other account notes", fixture.store.read("42").notes)
        }
    }

    @Test
    fun `current owner keeps strict input validation whitespace and optimistic conflicts`() {
        MemoryNoteOwnershipFixture(directory).use { fixture ->
            fixture.prepare("100", "Initial notes")
            val previous = fixture.component.get("42")
            assertFailsWith<ValidationFailedException> {
                fixture.component.edit("42", "x".repeat(16001), previous.revision)
            }
            assertEquals("Initial notes", fixture.store.read("42").notes)

            val notes = "    Synthetic indented note\nTrailing spaces  \n"
            val saved = fixture.component.edit("42", notes, previous.revision)
            assertEquals(notes, saved.notes)
            assertEquals("memory/100/42/MEMORY.md", saved.relativePath)
            assertFailsWith<MemoryConflictException> {
                fixture.component.edit("42", "Stale update", previous.revision)
            }
            assertEquals(notes, fixture.store.read("42").notes)
        }
    }

    @Test
    fun `global manual notes remain editable without Telegram authentication and shared across accounts`() {
        MemoryNoteOwnershipFixture(directory).use { fixture ->
            fixture.ownerId = ""
            val previous = fixture.component.get("")
            val saved = fixture.component.edit("", "Shared manual notes", previous.revision)
            assertEquals("Shared manual notes", saved.notes)
            assertEquals("memory/MEMORY.md", saved.relativePath)

            fixture.store.activate("100")
            assertEquals("Shared manual notes", fixture.store.read("").notes)
            fixture.store.activate("101")
            assertEquals("Shared manual notes", fixture.store.read("").notes)
        }
    }
}
