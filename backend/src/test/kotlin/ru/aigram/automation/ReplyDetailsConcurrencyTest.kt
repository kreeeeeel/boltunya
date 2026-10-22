package ru.aigram.automation

import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.automation.activity.ActivityHistoryStore
import ru.aigram.automation.activity.ReplyTrace
import ru.aigram.domain.ai.PreviewJob
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.chat.ChatSummary
import ru.aigram.domain.events.ActivityDetails
import ru.aigram.domain.events.ActivityEntry
import ru.aigram.domain.events.ActivityFeedback
import ru.aigram.domain.events.ActivityFeedbackRating
import ru.aigram.domain.events.ActivityFeedbackUpdate
import ru.aigram.domain.events.ActivityStatus
import ru.aigram.exception.impl.ActivityNotFoundException
import ru.aigram.resources.ResourceCatalog
import ru.aigram.storage.ActivityFeedbackStore
import ru.aigram.storage.SettingsStore
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReplyDetailsConcurrencyTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `archive read does not block queue pause cancel or adding a live draft`() {
        val gateway = ActivityDetailsGateway()
        val generated = AtomicInteger()
        store().use { history ->
            val archived = archive(history)
            coordinator(gateway, history).use { replies ->
                replies.pauseQueue(true)
                val pending = draft(replies, generated)
                ActivityArchiveBarrier(history).use { barrier ->
                    barrier.hold()
                    val reading = barrier.read { replies.details(archived.id) }
                    barrier.awaitReader()

                    val controls = Executors.newFixedThreadPool(3)
                    try {
                        val paused = controls.submit { replies.pauseQueue(true) }
                        val cancelled = controls.submit { replies.cancel(pending.id) }
                        val added = controls.submit<PreviewJob> { draft(replies, generated) }
                        paused.get(3, TimeUnit.SECONDS)
                        cancelled.get(3, TimeUnit.SECONDS)
                        val live = added.get(3, TimeUnit.SECONDS)
                        assertTrue(replies.queue().paused)
                        assertEquals("cancelled", replies.draft(pending.id).status)
                        assertEquals("queued", replies.draft(live.id).status)
                        assertFalse(reading.isDone)
                    } finally {
                        controls.shutdownNow()
                    }

                    barrier.release()
                    assertEquals("Synthetic archived response", reading.get(3, TimeUnit.SECONDS).response)
                    assertEquals(0, generated.get())
                    assertTrue(gateway.delegate.sent.isEmpty())
                    assertTrue(gateway.delegate.sentVoices.isEmpty())
                }
            }
        }
    }

    @Test
    fun `owner switch while archive is loading never returns the previous account trace`() {
        val gateway = ActivityDetailsGateway()
        store().use { history ->
            val archived = archive(history)
            coordinator(gateway, history).use { replies ->
                ActivityArchiveBarrier(history).use { barrier ->
                    barrier.hold()
                    val reading = barrier.read { replies.details(archived.id) }
                    barrier.awaitReader()
                    gateway.owner = "101"
                    gateway.delegate.chatSummaries = listOf(chat("/api/media/202"))
                    barrier.release()

                    val failure = assertFailsWith<ExecutionException> { reading.get(3, TimeUnit.SECONDS) }
                    assertIs<ActivityNotFoundException>(failure.cause)
                }
            }
        }
    }

    @Test
    fun `chat lookup releases the queue gate and enrichment observes updated priority and position`() {
        val gateway = ActivityDetailsGateway()
        coordinator(gateway).use { replies ->
            replies.pauseQueue(true)
            val generated = AtomicInteger()
            val first = draft(replies, generated)
            val selected = draft(replies, generated)
            val entered = CountDownLatch(1)
            val released = CountDownLatch(1)
            gateway.beforeChats = {
                entered.countDown()
                assertTrue(released.await(5, TimeUnit.SECONDS))
            }
            val workers = Executors.newFixedThreadPool(2)
            try {
                val reading = workers.submit<ActivityDetails> { replies.details(selected.id) }
                assertTrue(entered.await(3, TimeUnit.SECONDS))
                workers.submit {
                    replies.prioritize(selected.id)
                    replies.cancel(first.id)
                }.get(3, TimeUnit.SECONDS)
                assertFalse(reading.isDone)
                released.countDown()

                val details = reading.get(3, TimeUnit.SECONDS)
                assertTrue(details.entry.priority)
                assertEquals(1, details.entry.queuePosition)
                assertEquals(0, generated.get())
            } finally {
                released.countDown()
                gateway.beforeChats = {}
                workers.shutdownNow()
            }
        }
    }

    @Test
    fun `account is checked again after loading the current chat directory`() {
        val gateway = ActivityDetailsGateway()
        store().use { history ->
            val archived = archive(history)
            coordinator(gateway, history).use { replies ->
                val entered = CountDownLatch(1)
                val released = CountDownLatch(1)
                gateway.beforeChats = {
                    entered.countDown()
                    assertTrue(released.await(5, TimeUnit.SECONDS))
                }
                val worker = Executors.newSingleThreadExecutor()
                try {
                    val reading = worker.submit<ActivityDetails> { replies.details(archived.id) }
                    assertTrue(entered.await(3, TimeUnit.SECONDS))
                    gateway.owner = "101"
                    released.countDown()

                    val failure = assertFailsWith<ExecutionException> { reading.get(3, TimeUnit.SECONDS) }
                    assertIs<ActivityNotFoundException>(failure.cause)
                } finally {
                    released.countDown()
                    gateway.beforeChats = {}
                    worker.shutdownNow()
                }
            }
        }
    }

    @Test
    fun `archived details use current avatar and one current feedback snapshot after loading`() {
        val gateway = ActivityDetailsGateway()
        val feedback = ActivityFeedbackStore(directory, Clock.systemUTC())
        store().use { history ->
            val archived = archive(history)
            feedback.save(archived.id, ActivityFeedbackUpdate(ActivityFeedbackRating.GOOD, "Synthetic first rating"))
            coordinator(gateway, history, feedback).use { replies ->
                val entered = CountDownLatch(1)
                val released = CountDownLatch(1)
                gateway.beforeChats = {
                    entered.countDown()
                    assertTrue(released.await(5, TimeUnit.SECONDS))
                }
                val workers = Executors.newFixedThreadPool(2)
                try {
                    val reading = workers.submit<ActivityDetails> { replies.details(archived.id) }
                    assertTrue(entered.await(3, TimeUnit.SECONDS))
                    val updated = workers.submit<ActivityFeedback> {
                        replies.saveFeedback(
                            archived.id,
                            ActivityFeedbackUpdate(ActivityFeedbackRating.BAD, "Synthetic updated rating"),
                        )
                    }.get(3, TimeUnit.SECONDS)
                    gateway.delegate.chatSummaries = listOf(chat("/api/media/303"))
                    released.countDown()

                    val details = reading.get(3, TimeUnit.SECONDS)
                    assertEquals("Synthetic archived response", details.response)
                    assertEquals("/api/media/303", details.entry.avatarUrl)
                    assertEquals(ActivityFeedbackRating.BAD, details.entry.feedbackRating)
                    assertEquals(updated, details.feedback)
                } finally {
                    released.countDown()
                    gateway.beforeChats = {}
                    workers.shutdownNow()
                }
            }
        }
    }

    @Test
    fun `invalid archived JSON surfaces its error and leaves queue controls usable`() {
        val gateway = ActivityDetailsGateway()
        store().use { history ->
            val archived = archive(history)
            val key = archived.date.toString().padStart(20, '0') + "-" + archived.id
            Files.writeString(directory.resolve("activity/100/history/$key.trace.json"), "{")
            coordinator(gateway, history).use { replies ->
                assertFailsWith<SerializationException> { replies.details(archived.id) }
                val controls = Executors.newSingleThreadExecutor()
                try {
                    controls.submit {
                        replies.pauseQueue(true)
                        val queued = draft(replies, AtomicInteger())
                        replies.cancel(queued.id)
                        assertEquals("cancelled", replies.draft(queued.id).status)
                    }.get(3, TimeUnit.SECONDS)
                    assertTrue(replies.queue().paused)
                    assertTrue(gateway.delegate.sent.isEmpty())
                } finally {
                    controls.shutdownNow()
                }
            }
        }
    }

    private fun store() = ActivityHistoryStore(directory, Clock.systemUTC(), ResourceCatalog())

    private fun coordinator(
        gateway: ActivityDetailsGateway,
        history: ActivityHistoryStore? = null,
        feedback: ActivityFeedbackStore? = null,
    ) = ReplyCoordinator(SettingsStore(directory), gateway, history = history, feedback = feedback) { _, _ ->
        throw AssertionError("Synthetic fixture must never generate")
    }

    private fun draft(replies: ReplyCoordinator, generated: AtomicInteger) =
        replies.enqueueToolDraft("Synthetic live", "synthetic", "Synthetic input") {
            generated.incrementAndGet()
            ReplyResult("Synthetic unused", 0, 0.0)
        }

    private fun archive(history: ActivityHistoryStore): ActivityEntry {
        val trace = ReplyTrace(
            ActivityEntry(UUID.randomUUID().toString(), "42", "Synthetic owner100", "queued", "synthetic", 100),
            "Synthetic archived input",
        )
        trace.finish(ActivityStatus.READY, "synthetic", "Synthetic archived response")
        history.watch("100", trace)
        history.flush()
        return trace.summary()
    }

    private fun chat(avatar: String) = ChatSummary("42", "Synthetic person", "", 0, 0, ChatRule(), avatarUrl = avatar)
}
