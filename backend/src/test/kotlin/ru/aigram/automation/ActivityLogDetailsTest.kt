package ru.aigram.automation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.automation.activity.ActivityHistoryStore
import ru.aigram.automation.activity.ActivityLog
import ru.aigram.automation.activity.ReplyTrace
import ru.aigram.domain.events.ActivityEntry
import ru.aigram.domain.events.ActivityStatus
import ru.aigram.exception.impl.ActivityNotFoundException
import ru.aigram.resources.ResourceCatalog
import java.nio.file.Path
import java.time.Clock
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

class ActivityLogDetailsTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `archived details release the log monitor so live entries remain readable`() {
        store().use { history ->
            val archived = archive(history, "100", "Synthetic archived response")
            val log = ActivityLog(history) { "100" }
            ActivityArchiveBarrier(history).use { barrier ->
                barrier.hold()
                val reading = barrier.read { log.details(archived.id) }
                barrier.awaitReader()

                val writer = Executors.newSingleThreadExecutor()
                try {
                    val live = writer.submit<ReplyTrace> { log.add(entry(), "Synthetic live input") }
                        .get(3, TimeUnit.SECONDS)
                    live.finish(ActivityStatus.READY, "synthetic", "Synthetic live response")
                    assertEquals("Synthetic live response", log.details(live.summary().id).response)
                    assertEquals(listOf(live.summary().id), log.summaries().stream().map { it.id }.toList())
                    assertFalse(reading.isDone)
                } finally {
                    writer.shutdownNow()
                }

                barrier.release()
                val loaded = reading.get(3, TimeUnit.SECONDS)
                assertEquals(archived, loaded.entry)
                assertEquals("Synthetic archived response", loaded.response)
                assertEquals("Synthetic archived input", loaded.trigger)
            }
        }
    }

    @Test
    fun `explicit captured owner is reused rather than recaptured during archive lookup`() {
        store().use { history ->
            val archived = archive(history, "100", "Synthetic owner100 response")
            archive(history, "101", "Synthetic owner101 response", archived.id)
            val owner = AtomicReference("101")
            val log = ActivityLog(history, owner::get)
            ActivityArchiveBarrier(history).use { barrier ->
                barrier.hold()
                val reading = barrier.read { log.details(archived.id, "100") }
                barrier.awaitReader()
                owner.set("100")
                barrier.release()

                assertEquals("Synthetic owner100 response", reading.get(3, TimeUnit.SECONDS).response)
            }
        }
    }

    @Test
    fun `account change during archived load rejects its result`() {
        store().use { history ->
            val archived = archive(history, "100", "Synthetic owner100 response")
            val owner = AtomicReference("100")
            val log = ActivityLog(history, owner::get)
            ActivityArchiveBarrier(history).use { barrier ->
                barrier.hold()
                val reading = barrier.read { log.details(archived.id) }
                barrier.awaitReader()
                owner.set("101")
                barrier.release()

                val failure = assertFailsWith<ExecutionException> { reading.get(3, TimeUnit.SECONDS) }
                assertIs<ActivityNotFoundException>(failure.cause)
            }
        }
    }

    private fun store() = ActivityHistoryStore(directory, Clock.systemUTC(), ResourceCatalog())

    private fun archive(
        history: ActivityHistoryStore,
        owner: String,
        response: String,
        id: String = UUID.randomUUID().toString(),
    ): ActivityEntry {
        val trace = ReplyTrace(entry(id), "Synthetic archived input")
        trace.finish(ActivityStatus.READY, "synthetic", response)
        history.watch(owner, trace)
        history.flush()
        return trace.summary()
    }

    private fun entry(id: String = UUID.randomUUID().toString()) =
        ActivityEntry(id, "42", "Synthetic person", "queued", "synthetic", 100)
}
