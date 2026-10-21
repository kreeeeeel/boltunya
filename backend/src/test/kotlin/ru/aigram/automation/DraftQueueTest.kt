package ru.aigram.automation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.ActivityKind
import ru.aigram.domain.ChatRule
import ru.aigram.domain.ReplyResult
import ru.aigram.storage.SettingsStore
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.IntStream
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DraftQueueTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `manual draft respects shared queue pause and works while automatic replies are paused`() {
        val gateway = FakeTelegramGateway()
        ReplyCoordinator(SettingsStore(directory), gateway) { _, _ ->
            throw AssertionError("Automatic generator must not be used")
        }.use { queue ->
            queue.pauseQueue(true)
            val runs = AtomicInteger()
            val job = queue.enqueueDraft("42", "test-model") {
                runs.incrementAndGet()
                ReplyResult("Draft only", 10, 2.0, "test-model")
            }
            assertEquals("queued", queue.draft(job.id).status)
            assertEquals(ActivityKind.DRAFT, queue.details(job.id).entry.kind)
            assertEquals(0, runs.get())
            queue.pauseQueue(false)
            await { queue.draft(job.id).status == "ready" }
            assertEquals("Draft only", queue.draft(job.id).result?.text)
            assertEquals("Draft only", queue.details(job.id).response)
            assertTrue(queue.details(job.id).entry.feedbackAvailable)
            assertTrue(gateway.sent.isEmpty())
            assertEquals(1, runs.get())
        }
    }

    @Test
    fun `queued draft can be reprioritized or cancelled without invoking generation`() {
        val gateway = FakeTelegramGateway()
        ReplyCoordinator(SettingsStore(directory), gateway) { _, _ -> ReplyResult("auto", 0, 0.0) }.use { queue ->
            queue.pauseQueue(true)
            val order = CopyOnWriteArrayList<String>()
            val first = queue.enqueueDraft("42", "test") {
                order.add("first")
                ReplyResult("first", 0, 0.0)
            }
            val cancelled = queue.enqueueDraft("43", "test") {
                throw AssertionError("Cancelled generation must not run")
            }
            val prioritized = queue.enqueueDraft("43", "test") {
                order.add("priority")
                ReplyResult("priority", 0, 0.0)
            }
            queue.cancel(cancelled.id)
            queue.prioritize(prioritized.id)
            queue.pauseQueue(false)
            await { queue.draft(first.id).status == "ready" }
            assertEquals(listOf("priority", "first"), order.toList())
            assertEquals("cancelled", queue.draft(cancelled.id).status)
            assertNull(queue.draft(cancelled.id).result)
            assertTrue(gateway.sent.isEmpty())
        }
    }

    @Test
    fun `manual draft waits for automatic inference on the same worker and never delivers itself`() {
        val settings = SettingsStore(directory)
        settings.update {
            it.copy(
                paused = false,
                replyDelaySeconds = 0,
                replyIntervalSeconds = 0,
                rules = mapOf("42" to ChatRule(enabled = true)),
            )
        }
        val gateway = FakeTelegramGateway()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        ReplyCoordinator(settings, gateway) { _, _ ->
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            ReplyResult("Automatic answer", 0, 0.0)
        }.use { queue ->
            queue.onMessage(gateway.messages.first())
            assertTrue(started.await(3, TimeUnit.SECONDS))
            val draft = queue.enqueueDraft("43", "test") { ReplyResult("Manual draft", 0, 0.0) }
            assertEquals("queued", queue.draft(draft.id).status)
            release.countDown()
            await { queue.draft(draft.id).status == "ready" }
            assertEquals(listOf("Automatic answer"), gateway.sent.toList())
            assertEquals("Manual draft", queue.draft(draft.id).result?.text)
        }
    }

    @Test
    fun `active draft cancellation interrupts inference and discards late output`() {
        val gateway = FakeTelegramGateway()
        val started = CountDownLatch(1)
        ReplyCoordinator(SettingsStore(directory), gateway) { _, _ -> ReplyResult("auto", 0, 0.0) }.use { queue ->
            val draft = queue.enqueueDraft("42", "test") {
                started.countDown()
                CountDownLatch(1).await(5, TimeUnit.SECONDS)
                ReplyResult("Late draft", 0, 0.0)
            }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            queue.cancel(draft.id)
            await { queue.draft(draft.id).status == "cancelled" }
            assertNull(queue.draft(draft.id).result)
            assertTrue(gateway.sent.isEmpty())
        }
    }

    private fun await(condition: () -> Boolean) {
        assertTrue(
            IntStream.range(0, 300).anyMatch {
                if (condition()) {
                    true
                } else {
                    TimeUnit.MILLISECONDS.sleep(10)
                    false
                }
            },
        )
    }
}
