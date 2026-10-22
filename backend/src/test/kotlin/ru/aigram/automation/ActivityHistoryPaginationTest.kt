package ru.aigram.automation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.automation.activity.ActivityHistoryStore
import ru.aigram.automation.activity.ReplyTrace
import ru.aigram.domain.events.ActivityEntry
import ru.aigram.domain.events.ActivityHistorySettings
import ru.aigram.domain.events.ActivityStatus
import ru.aigram.exception.impl.ActivityNotFoundException
import ru.aigram.exception.impl.MalformedPayloadException
import ru.aigram.resources.ResourceCatalog
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ActivityHistoryPaginationTest {
    @TempDir lateinit var directory: Path
    private val clock = Clock.fixed(Instant.ofEpochSecond(200000), ZoneOffset.UTC)

    @Test
    fun `same timestamp pages order UUID ties and exclude inserts newer than the cursor`() {
        store().use { history ->
            listOf(2L, 4L, 6L, 8L).stream().forEach { save(history, "100", 199000, it) }
            val first = history.page("100", null, 2)
            assertEquals(listOf(id(8), id(6)), first.entries.stream().map { it.id }.toList())
            assertTrue(first.hasMore)
            val after = assertNotNull(first.nextCursor)

            save(history, "100", 199000, 10)
            save(history, "100", 199000, 5)
            val second = history.page("100", after, 2)
            assertEquals(listOf(id(5), id(4)), second.entries.stream().map { it.id }.toList())
            assertEquals(6, second.total)
            assertTrue(second.hasMore)

            val last = history.page("100", assertNotNull(second.nextCursor), 2)
            assertEquals(listOf(id(2)), last.entries.stream().map { it.id }.toList())
            assertFalse(last.hasMore)
            assertEquals(null, last.nextCursor)
            assertTrue(history.page("100", cursor(199000, 1), 2).entries.isEmpty())
        }
    }

    @Test
    fun `missing cursor keys seek the next older record without requiring an archived event`() {
        store().use { history ->
            listOf(2L, 4L, 6L, 8L).stream().forEach { save(history, "100", 199000, it) }
            val page = history.page("100", cursor(199000, 7), 2)
            assertEquals(listOf(id(6), id(4)), page.entries.stream().map { it.id }.toList())
            assertTrue(page.hasMore)
            assertEquals(4, page.total)
        }
    }

    @Test
    fun `cursor survives removal and restart while page and details remain account scoped`() {
        val cursor = store().use { history ->
            listOf(2L, 4L, 6L, 8L).stream().forEach { save(history, "100", 199000, it) }
            save(history, "101", 199000, 2)
            assertNotNull(history.page("100", null, 2).nextCursor)
        }
        listOf("entry", "trace").stream().forEach { suffix ->
            Files.delete(historyDirectory("100").resolve("${key(199000, 6)}.$suffix.json"))
        }

        store().use { history ->
            val page = history.page("100", cursor, 2)
            assertEquals(listOf(id(4), id(2)), page.entries.stream().map { it.id }.toList())
            assertEquals(3, page.total)
            assertFalse(page.hasMore)
            assertEquals(listOf(id(2)), history.page("101", cursor, 2).entries.stream().map { it.id }.toList())
            assertEquals("Synthetic owner 101", history.details("101", id(2)).entry.title)
            assertEquals("Synthetic owner 100", history.details("100", id(2)).entry.title)
            assertFailsWith<ActivityNotFoundException> { history.details("101", id(4)) }
            assertEquals(0, history.page("102", cursor, 2).total)
        }
    }

    @Test
    fun `retention removes only dates strictly before cutoff and updates stored byte accounting`() {
        val cutoff = 200000L - 86400
        store().use { history ->
            save(history, "100", cutoff - 1, 1)
            save(history, "100", cutoff - 1, 2)
            save(history, "100", cutoff, 3)
            save(history, "100", cutoff, 4)
            save(history, "100", cutoff + 1, 5)
            val before = history.storage("100").bytes
            assertEquals(storedBytes("100"), before)

            val storage = history.configure("100", ActivityHistorySettings(retentionDays = 1))
            val page = history.page("100", null, 100)
            assertEquals(listOf(id(5), id(4), id(3)), page.entries.stream().map { it.id }.toList())
            assertEquals(3, page.total)
            assertTrue(storage.bytes < before)
            assertEquals(storedBytes("100"), storage.bytes)
            assertEquals(storage.bytes, page.storage.bytes)
            assertEquals(storage.bytes, history.page("100", null, 100).storage.bytes)
            assertFailsWith<ActivityNotFoundException> { history.details("100", id(1)) }
            assertFailsWith<ActivityNotFoundException> { history.details("100", id(2)) }
            assertEquals(id(3), history.details("100", id(3)).entry.id)
        }

        store().use { history ->
            val page = history.page("100", null, 100)
            assertEquals(listOf(id(5), id(4), id(3)), page.entries.stream().map { it.id }.toList())
            assertEquals(storedBytes("100"), history.storage("100").bytes)
        }
    }

    @Test
    fun `retention preserves epoch zero when its cutoff is zero or negative`() {
        val epochClock = Clock.fixed(Instant.ofEpochSecond(86400), ZoneOffset.UTC)
        store(epochClock).use { history ->
            save(history, "100", 0, 1)
            save(history, "100", 1, 2)
            history.configure("100", ActivityHistorySettings(retentionDays = 1))
            val atEpoch = history.page("100", null, 100)
            assertEquals(listOf(id(2), id(1)), atEpoch.entries.stream().map { it.id }.toList())
            history.configure("100", ActivityHistorySettings(retentionDays = 365))
            val beforeEpoch = history.page("100", null, 100)
            assertEquals(listOf(id(2), id(1)), beforeEpoch.entries.stream().map { it.id }.toList())
            assertEquals(storedBytes("100"), history.storage("100").bytes)
        }
    }

    @Test
    fun `ordered page selection preserves the page size validation boundary`() {
        store().use { history ->
            save(history, "100", 199000, 1)
            assertEquals(1, history.page("100", null, 100).entries.size)
            assertFailsWith<MalformedPayloadException> { history.page("100", null, 0) }
            assertFailsWith<MalformedPayloadException> { history.page("100", null, 101) }
        }
    }

    private fun store(currentClock: Clock = clock) = ActivityHistoryStore(directory, currentClock, ResourceCatalog())

    private fun save(history: ActivityHistoryStore, owner: String, date: Long, sequence: Long) {
        val trace = ReplyTrace(
            ActivityEntry(id(sequence), "42", "Synthetic owner $owner", "queued", "model", date),
            "Synthetic input",
        )
        trace.finish(ActivityStatus.READY, "model", "Synthetic response")
        history.watch(owner, trace)
        history.flush()
    }

    private fun id(sequence: Long): String = UUID(0, sequence).toString()

    private fun key(date: Long, sequence: Long): String = date.toString().padStart(20, '0') + "-" + id(sequence)

    private fun cursor(date: Long, sequence: Long): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(key(date, sequence).toByteArray(Charsets.UTF_8))

    private fun historyDirectory(owner: String): Path = directory.resolve("activity/$owner/history")

    private fun storedBytes(owner: String): Long = Files.walk(directory.resolve("activity/$owner")).use { files ->
        files.filter { Files.isRegularFile(it) }.mapToLong { Files.size(it) }.sum()
    }
}
