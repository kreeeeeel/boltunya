package ru.aigram.automation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.ai.chat.messages.UserMessage
import ru.aigram.ai.model.ModelCompletion
import ru.aigram.automation.activity.ReplyTraceScope
import ru.aigram.domain.ActivityKind
import ru.aigram.domain.ReplyResult
import ru.aigram.exception.impl.BackgroundInferenceDeferredException
import ru.aigram.storage.SettingsStore
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.IntStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SharedInferenceQueueTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `parallel limit bounds independent draft workers and is persisted`() {
        val settings = SettingsStore(directory)
        settings.update { it.copy(maxParallelTasks = 2) }
        assertEquals(2, SettingsStore(directory).get().maxParallelTasks)
        val started = CountDownLatch(2)
        val release = CountDownLatch(1)
        val running = AtomicInteger()
        val peak = AtomicInteger()
        ReplyCoordinator(settings, FakeTelegramGateway()) { _, _ -> ReplyResult("auto", 0, 0.0) }.use { queue ->
            val jobs = IntStream.range(0, 3).mapToObj {
                queue.enqueueDraft("42", "test") {
                    peak.accumulateAndGet(running.incrementAndGet(), ::maxOf)
                    started.countDown()
                    try {
                        release.await(3, TimeUnit.SECONDS)
                        ReplyResult("draft", 0, 0.0)
                    } finally {
                        running.decrementAndGet()
                    }
                }
            }.toList()
            try {
                assertTrue(started.await(2, TimeUnit.SECONDS))
                assertEquals(2, queue.queue().activeTasks)
                assertEquals(1, queue.queue().queued)
                assertEquals(2, peak.get())
            } finally {
                release.countDown()
            }
            await { jobs.all { queue.draft(it.id).status == "ready" } }
            assertEquals(2, peak.get())
        }
    }

    @Test
    fun `paused shared queue orders automatic memory behind a draft and records metadata only`() {
        val tracing = ReplyTraceScope()
        val order = CopyOnWriteArrayList<String>()
        val gateway = FakeTelegramGateway()
        ReplyCoordinator(SettingsStore(directory), gateway, tracing = tracing) { _, _ ->
            ReplyResult("auto", 0, 0.0)
        }.use { queue ->
            queue.pauseQueue(true)
            val task = memory(action = {
                order.add("memory")
                val trace = assertNotNull(tracing.current())
                val attempt = trace.begin(
                    "private-model",
                    listOf(UserMessage("private source message")),
                    "sampling metadata",
                )
                attempt.finish(
                    ModelCompletion(
                        "private facts",
                        12,
                        4.0,
                        "stop",
                        thinking = "private reasoning",
                        inputTokens = 10,
                        outputTokens = 3,
                    ),
                )
                trace.generated("private facts")
            })
            queue.enqueueMemory(task)
            val draft = queue.enqueueDraft("43", "test") {
                order.add("draft")
                ReplyResult("only a draft", 0, 0.0)
            }
            assertEquals("queued", queue.details(task.id).entry.status)
            assertEquals(2, queue.details(task.id).entry.queuePosition)
            assertTrue(order.isEmpty())
            queue.pauseQueue(false)
            await { queue.details(task.id).entry.status == "ready" }
            assertEquals(listOf("draft", "memory"), order.toList())
            assertEquals("ready", queue.draft(draft.id).status)
            val details = queue.details(task.id)
            assertEquals(ActivityKind.MEMORY, details.entry.kind)
            assertEquals("", details.trigger)
            assertEquals("", details.response)
            assertEquals("", details.entry.preview)
            assertFalse(details.entry.feedbackAvailable)
            val attempt = details.attempts.single()
            assertTrue(attempt.messages.isEmpty())
            assertEquals("", attempt.response)
            assertEquals("", attempt.thinking)
            assertEquals(10, details.entry.inputTokens)
            assertEquals(3, details.entry.outputTokens)
            assertTrue(gateway.sent.isEmpty())
        }
    }

    @Test
    fun `new draft preempts automatic memory and preserves its deferred checkpoint signal`() {
        val started = CountDownLatch(1)
        val completed = AtomicReference<Throwable?>()
        val finished = CountDownLatch(1)
        ReplyCoordinator(SettingsStore(directory), FakeTelegramGateway()) { _, _ ->
            ReplyResult("auto", 0, 0.0)
        }.use { queue ->
            val task = memory(action = {
                started.countDown()
                CountDownLatch(1).await(5, TimeUnit.SECONDS)
            }, completed = {
                completed.set(it)
                finished.countDown()
            })
            queue.enqueueMemory(task)
            assertTrue(started.await(2, TimeUnit.SECONDS))
            val draft = queue.enqueueDraft("43", "test") { ReplyResult("foreground", 0, 0.0) }
            assertTrue(finished.await(2, TimeUnit.SECONDS))
            assertIs<BackgroundInferenceDeferredException>(completed.get())
            await { queue.draft(draft.id).status == "ready" }
            assertEquals("cancelled", queue.details(task.id).entry.status)
        }
    }

    @Test
    fun `explicit collection is not preempted and active cancellation invokes its collection callback`() {
        val started = CountDownLatch(1)
        val cancelled = AtomicInteger()
        val finished = CountDownLatch(1)
        val draftStarted = CountDownLatch(1)
        ReplyCoordinator(SettingsStore(directory), FakeTelegramGateway()) { _, _ ->
            ReplyResult("auto", 0, 0.0)
        }.use { queue ->
            val task = memory(explicit = true, action = {
                started.countDown()
                CountDownLatch(1).await(5, TimeUnit.SECONDS)
            }, cancelled = { cancelled.incrementAndGet() }, completed = { finished.countDown() })
            queue.enqueueMemory(task)
            assertTrue(started.await(2, TimeUnit.SECONDS))
            assertTrue(queue.details(task.id).entry.priority)
            val draft = queue.enqueueDraft("43", "test") {
                draftStarted.countDown()
                ReplyResult("foreground", 0, 0.0)
            }
            assertFalse(draftStarted.await(200, TimeUnit.MILLISECONDS))
            assertEquals("generating", queue.details(task.id).entry.status)
            queue.cancel(task.id)
            assertEquals(1, cancelled.get())
            assertTrue(finished.await(2, TimeUnit.SECONDS))
            await { queue.draft(draft.id).status == "ready" }
        }
    }

    @Test
    fun `queued memory can be prioritized or cancelled without reading history`() {
        val order = CopyOnWriteArrayList<String>()
        val cancelled = AtomicInteger()
        val gateway = FakeTelegramGateway()
        ReplyCoordinator(SettingsStore(directory), gateway) { _, _ -> ReplyResult("auto", 0, 0.0) }.use { queue ->
            queue.pauseQueue(true)
            val deleted = memory(
                action = { throw AssertionError("Cancelled memory must not run") },
                cancelled = { cancelled.incrementAndGet() },
            )
            val priority = memory(action = { order.add("memory") })
            queue.enqueueMemory(deleted)
            queue.enqueueMemory(priority)
            val draft = queue.enqueueDraft("43", "test") {
                order.add("draft")
                ReplyResult("draft", 0, 0.0)
            }
            queue.cancel(deleted.id)
            queue.prioritize(priority.id)
            assertEquals(1, queue.details(priority.id).entry.queuePosition)
            assertTrue(queue.details(priority.id).entry.priority)
            assertEquals(1, cancelled.get())
            queue.pauseQueue(false)
            await { queue.draft(draft.id).status == "ready" }
            assertEquals(listOf("memory", "draft"), order.toList())
            assertTrue(gateway.sent.isEmpty())
        }
    }

    private fun memory(
        explicit: Boolean = false,
        action: () -> Unit,
        cancelled: () -> Unit = {},
        completed: (Throwable?) -> Unit = {},
    ) = MemoryQueueTask(
        UUID.randomUUID().toString(), "42", "100", "Chat", "", "test", explicit,
        canStart = { true }, action = action, userCancelled = cancelled, completed = completed,
    )

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
