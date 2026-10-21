package ru.aigram.automation

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.ai.chat.messages.UserMessage
import ru.aigram.automation.activity.ActivityHistoryStore
import ru.aigram.automation.activity.ReplyTrace
import ru.aigram.domain.ai.model.ModelCompletion
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
import java.util.UUID
import java.util.stream.IntStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ActivityHistoryStoreTest {
    @TempDir lateinit var directory: Path
    private val clock = Clock.fixed(Instant.ofEpochSecond(200000), ZoneOffset.UTC)

    @Test
    fun `archive retains more than a session window and stable cursor survives inserts and restart`() {
        val all = IntStream.range(0, 135).mapToObj { trace(1000L + it) }.toList()
        store().use { history ->
            all.stream().forEach { history.watch("100", it) }
            history.flush()
            val first = history.page("100", null, 30)
            assertEquals(135, first.total)
            assertTrue(first.hasMore)
            history.watch("100", trace(2000))
            history.flush()
            val second = history.page("100", first.nextCursor, 30)
            assertTrue(first.entries.none { entry -> second.entries.any { it.id == entry.id } })
            assertEquals(1104, second.entries.first().date)
            assertEquals(0, history.page("101", null, 30).total)
        }
        store().use { history ->
            assertEquals(136, history.page("100", null, 100).total)
            val details = history.details("100", all.first().summary().id)
            assertEquals("Owner's final reply", details.response)
            assertEquals("Private incoming message", details.attempts.single().messages.single().text)
            assertEquals("Model thinking", details.attempts.single().thinking)
            assertEquals(7, details.entry.outputTokens)
            assertFailsWith<ActivityNotFoundException> { history.details("101", details.entry.id) }
            assertFailsWith<MalformedPayloadException> { history.page("100", "../../private", 30) }
        }
    }

    @Test
    fun `stale active snapshot never overwrites a completed trace after a crash`() {
        val completed = trace(123)
        val id = completed.summary().id
        store().use { history ->
            history.watch("100", completed)
            history.flush()
        }
        val active = directory.resolve("activity/100/active/$id.json")
        Files.createDirectories(active.parent)
        val stale = completed.details().copy(entry = completed.summary().copy(status = "generating", phase = "reply"))
        Files.writeString(active, Json.encodeToString(stale))
        store().use { history ->
            assertEquals("ready", history.page("100", null, 30).entries.single().status)
            assertEquals("Owner's final reply", history.details("100", id).response)
            assertFalse(Files.exists(active))
        }
    }

    @Test
    fun `clearing an unsaved terminal task also removes its active checkpoint`() {
        val trace = ReplyTrace(
            ActivityEntry(UUID.randomUUID().toString(), "42", "Person", "queued", "model", 123),
            "Incoming",
        )
        store().use { history ->
            history.watch("100", trace)
            history.flush()
            history.configure("100", ActivityHistorySettings(maxStorageMb = 1))
            trace.finish(ActivityStatus.READY, "model", "x".repeat(1100000))
            history.flush()
            assertFalse(history.storage("100").recording)
            history.clear("100")
        }
        store().use { history ->
            assertTrue(history.page("100", null, 30).entries.isEmpty())
        }
    }

    @Test
    fun `pending jobs survive restart as interrupted without pretending to send or resume`() {
        val entry = ActivityEntry(UUID.randomUUID().toString(), "42", "Person", "queued", "model", 123)
        store().use { history ->
            history.watch("100", ReplyTrace(entry, "Incoming message"))
            history.flush()
            assertTrue(history.page("100", null, 30).entries.isEmpty())
        }
        store().use { history ->
            val restored = history.page("100", null, 30).entries.single()
            assertEquals("interrupted", restored.status)
            assertEquals("interrupted", restored.phase)
            assertEquals(entry.id, restored.id)
            assertEquals("Incoming message", history.details("100", entry.id).trigger)
        }
    }

    @Test
    fun `retention is opt in and clearing one account never removes another account`() {
        store().use { history ->
            history.watch("100", trace(1))
            history.watch("100", trace(199000))
            val other = trace(2)
            history.watch("101", other)
            history.flush()
            assertEquals(0, history.storage("100").retentionDays)
            assertEquals(0, history.storage("100").maxStorageMb)
            assertEquals(2, history.page("100", null, 30).total)
            history.configure("100", ActivityHistorySettings(retentionDays = 1))
            assertEquals(1, history.page("100", null, 30).total)
            history.watch("101", trace(199001))
            history.flush()
            history.clear("100")
            assertEquals(0, history.page("100", null, 30).total)
            assertEquals(1, history.page("101", null, 30).total)
        }
    }

    @Test
    fun `explicit quota reports stopped recording and retries after removal without deleting old records`() {
        store().use { history ->
            val old = trace(1)
            history.watch("100", old)
            history.flush()
            history.configure("100", ActivityHistorySettings(maxStorageMb = 1))
            val large = trace(2, "x".repeat(1100000))
            history.watch("100", large)
            history.flush()
            assertFalse(history.storage("100").recording)
            assertEquals("ACTIVITY_HISTORY_QUOTA_EXCEEDED", history.storage("100").errorCode)
            assertNotNull(history.details("100", old.summary().id))
            history.configure("100", ActivityHistorySettings())
            history.flush()
            assertTrue(history.storage("100").recording)
            assertEquals(2, history.page("100", null, 30).total)
        }
    }

    @Test
    fun `quota failure remains visible across successful smaller writes and blocks new pending references`() {
        store().use { history ->
            history.configure("100", ActivityHistorySettings(maxStorageMb = 1))
            IntStream.range(1, 9).forEach { history.watch("100", trace(it.toLong())) }
            history.watch("100", trace(9, "x".repeat(1100000)))
            history.flush()
            assertFalse(history.storage("100").recording)
            assertEquals("ACTIVITY_HISTORY_QUOTA_EXCEEDED", history.storage("100").errorCode)
            history.watch("100", trace(10))
            history.configure("100", ActivityHistorySettings())
            history.flush()
            assertTrue(history.storage("100").recording)
            assertEquals(9, history.page("100", null, 30).total)
        }
    }

    @Test
    fun `disk failure stops accepting more traces and retries the already pending record`() {
        val blocked = directory.resolve("activity/100/history")
        Files.createDirectories(blocked.parent)
        Files.writeString(blocked, "Blocked directory")
        store().use { history ->
            history.watch("100", trace(1))
            history.flush()
            history.watch("100", trace(2))
            Files.delete(blocked)
            history.flush()
            assertTrue(history.storage("100").recording)
            assertEquals(1, history.page("100", null, 30).total)
        }
    }

    private fun store() = ActivityHistoryStore(directory, clock, ResourceCatalog())

    private fun trace(date: Long, response: String = "Owner's final reply"): ReplyTrace {
        val trace = ReplyTrace(
            ActivityEntry(UUID.randomUUID().toString(), "42", "Person", "queued", "model", date),
            "Incoming",
        )
        trace.start()
        trace.begin("model", listOf(UserMessage("Private incoming message")), "safe model options").finish(
            ModelCompletion(response, 1, 7.0, "stop", thinking = "Model thinking", outputTokens = 7),
        )
        trace.finish(ActivityStatus.READY, "model", response)
        return trace
    }
}
