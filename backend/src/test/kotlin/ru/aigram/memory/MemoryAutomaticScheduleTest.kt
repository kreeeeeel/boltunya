package ru.aigram.memory

import jakarta.inject.Provider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.ModelInference
import ru.aigram.ai.OllamaRuntime
import ru.aigram.automation.MemoryWorkQueue
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ChatMessage
import ru.aigram.domain.ChatRule
import ru.aigram.domain.ReplyResult
import ru.aigram.exception.impl.MemoryUpdateFailedException
import ru.aigram.resources.ResourceCatalog
import ru.aigram.storage.AutomatedReplyStore
import ru.aigram.storage.SettingsStore
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.IntStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemoryAutomaticScheduleTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `automatic incomplete backfill creates one event per configured interval`() {
        val settings = settings()
        val telegram = telegram(250)
        val store = MemoryStore(directory, ResourceCatalog())
        val clock = AdvancingClock(Instant.ofEpochSecond(100000))
        ReplyCoordinator(settings, telegram) { _, _ -> ReplyResult("Unused", 0, 0.0) }.use { queue ->
            MemoryModelServer().use { server ->
                coordinator(settings, telegram, store, server.start().runtime, clock, queue).use { memory ->
                    memory.tick()
                    await { queue.activities().singleOrNull()?.status == "ready" }
                    assertEquals(100L, store.state("42")?.processedMessages)
                    assertFalse(store.state("42")?.backfillComplete == true)
                    assertEquals(100000L, store.state("42")?.automaticAttemptAt)
                    clock.advance(Duration.ofMinutes(5))
                    IntStream.range(0, 12).forEach { memory.tick() }
                    assertEquals(1, telegram.reads.size)
                    assertEquals(1, queue.activities().size)
                    assertEquals(MemoryStatus.READY, memory.document("42").status)
                    assertEquals(MemoryStatus.READY, memory.document("").status)
                    clock.advance(Duration.ofMinutes(115))
                    memory.tick()
                    await { queue.activities().count { it.status == "ready" } == 2 }
                    assertEquals(200L, store.state("42")?.processedMessages)
                    assertEquals(250L, store.state("42")?.headId)
                    assertEquals(107200L, store.state("42")?.automaticAttemptAt)
                }
            }
        }
    }

    @Test
    fun `failed automatic collection waits hours and keeps its schedule across restart`() {
        val settings = settings()
        val telegram = telegram(1)
        val clock = AdvancingClock(Instant.ofEpochSecond(100000))
        val attempts = AtomicInteger()
        val summarizer = MemorySummarizer { _, _, _ ->
            attempts.incrementAndGet()
            throw MemoryUpdateFailedException()
        }
        MemoryModelServer().use { server ->
            val runtime = server.start().runtime
            val store = MemoryStore(directory, ResourceCatalog())
            coordinator(settings, telegram, store, runtime, clock, summarizer = summarizer).use { memory ->
                memory.tick()
                assertEquals(1, attempts.get())
                assertEquals(0L, store.state("42")?.headId)
                assertEquals(100000L, store.state("42")?.automaticAttemptAt)
                clock.advance(Duration.ofMinutes(5))
                memory.tick()
                assertEquals(1, attempts.get())
            }
            val restored = MemoryStore(directory, ResourceCatalog())
            coordinator(settings, telegram, restored, runtime, clock, summarizer = summarizer).use { memory ->
                memory.tick()
                assertEquals(1, attempts.get())
                assertEquals(100000L, restored.state("42")?.automaticAttemptAt)
                clock.advance(Duration.ofMinutes(115))
                memory.tick()
                assertEquals(2, attempts.get())
                assertEquals(107200L, restored.state("42")?.automaticAttemptAt)
            }
        }
    }

    @Test
    fun `legacy completed checkpoints honor their interval when startup finds newer messages`() {
        val settings = settings()
        val telegram = telegram(2)
        val clock = AdvancingClock(Instant.ofEpochSecond(100000))
        val store = MemoryStore(directory, ResourceCatalog())
        store.activate("100")
        store.observe("42", 1)
        val previous = store.state("42") ?: error("Missing state")
        assertTrue(
            store.commit(
                previous,
                store.read("42").documentRevision,
                "Existing fact",
                previous.copy(
                    headId = 1,
                    observedId = 1,
                    backfillComplete = true,
                    dirty = false,
                    updatedAt = 100000,
                    recentUpdatedAt = 100000,
                    ownerMemoryVersion = 1,
                ),
            ),
        )
        val restored = MemoryStore(directory, ResourceCatalog())
        MemoryModelServer().use { server ->
            coordinator(settings, telegram, restored, server.start().runtime, clock).use { memory ->
                memory.tick()
                assertTrue(telegram.reads.isEmpty())
                assertEquals(0L, restored.state("42")?.automaticAttemptAt)
                assertEquals(2L, restored.state("42")?.observedId)
                assertFalse(restored.state("42")?.requested == true)
                clock.advance(Duration.ofHours(2).minusSeconds(1))
                memory.tick()
                assertTrue(telegram.reads.isEmpty())
                clock.advance(Duration.ofSeconds(1))
                memory.tick()
                assertEquals(2L, restored.state("42")?.headId)
                assertEquals(listOf("42" to 0L), telegram.reads)
            }
        }
    }

    @Test
    fun `automatic delta continues its persisted cursor on later intervals until it reaches the old head`() {
        val settings = settings()
        val telegram = telegram(251)
        val clock = AdvancingClock(Instant.ofEpochSecond(107200))
        val store = MemoryStore(directory, ResourceCatalog())
        store.activate("100")
        store.observe("42", 1)
        val previous = store.state("42") ?: error("Missing state")
        assertTrue(
            store.commit(
                previous,
                store.read("42").documentRevision,
                "Existing fact",
                previous.copy(
                    headId = 1,
                    observedId = 1,
                    backfillComplete = true,
                    dirty = false,
                    updatedAt = 100000,
                    recentUpdatedAt = 100000,
                    ownerMemoryVersion = 1,
                ),
            ),
        )
        MemoryModelServer().use { server ->
            coordinator(settings, telegram, store, server.start().runtime, clock).use { memory ->
                memory.tick()
                assertEquals(1L, store.state("42")?.headId)
                assertEquals(251L, store.state("42")?.deltaTargetId)
                assertEquals(152L, store.state("42")?.deltaBeforeId)
                clock.advance(Duration.ofMinutes(5))
                memory.tick()
                assertEquals(1, telegram.reads.size)
                clock.advance(Duration.ofMinutes(115))
                memory.tick()
                assertEquals(1L, store.state("42")?.headId)
                assertEquals(52L, store.state("42")?.deltaBeforeId)
                clock.advance(Duration.ofHours(2))
                memory.tick()
                assertEquals(251L, store.state("42")?.headId)
                assertEquals(0L, store.state("42")?.deltaTargetId)
                assertEquals(0L, store.state("42")?.deltaBeforeId)
                assertEquals(listOf("42" to 0L, "42" to 152L, "42" to 52L), telegram.reads)
            }
        }
    }

    @Test
    fun `manual deep collection bypasses the automatic interval and finishes all pages`() {
        val settings = settings()
        val telegram = telegram(250)
        val store = MemoryStore(directory, ResourceCatalog())
        val clock = AdvancingClock(Instant.ofEpochSecond(100000))
        ReplyCoordinator(settings, telegram) { _, _ -> ReplyResult("Unused", 0, 0.0) }.use { queue ->
            MemoryModelServer().use { server ->
                coordinator(settings, telegram, store, server.start().runtime, clock, queue).use { memory ->
                    memory.tick()
                    await { queue.activities().singleOrNull()?.status == "ready" }
                    memory.refresh("42", immediate = true)
                    await { store.state("42")?.backfillComplete == true && !memory.document("42").collection.active }
                    assertEquals(250L, store.state("42")?.processedMessages)
                    assertEquals(250L, store.state("42")?.headId)
                    assertEquals(100000L, clock.instant().epochSecond)
                    assertEquals(100000L, store.state("42")?.automaticAttemptAt)
                    assertTrue(telegram.reads.size > 2)
                    val completed = queue.activities().size
                    memory.tick()
                    assertEquals(completed, queue.activities().size)
                }
            }
        }
    }

    @Test
    fun `cancelling queued automatic collection keeps the cooldown after restart`() {
        val settings = settings()
        val telegram = telegram(1)
        val store = MemoryStore(directory, ResourceCatalog())
        val clock = AdvancingClock(Instant.ofEpochSecond(100000))
        ReplyCoordinator(settings, telegram) { _, _ -> ReplyResult("Unused", 0, 0.0) }.use { queue ->
            queue.pauseQueue(true)
            MemoryModelServer().use { server ->
                val runtime = server.start().runtime
                coordinator(settings, telegram, store, runtime, clock, queue).use { memory ->
                    memory.tick()
                    assertEquals(0L, store.state("42")?.automaticAttemptAt)
                    memory.cancel("42")
                    await { queue.activities().singleOrNull()?.status == "cancelled" }
                    assertEquals(100000L, store.state("42")?.automaticAttemptAt)
                    assertTrue(telegram.reads.isEmpty())
                }
                queue.pauseQueue(false)
                val restored = MemoryStore(directory, ResourceCatalog())
                coordinator(settings, telegram, restored, runtime, clock, queue).use { memory ->
                    memory.tick()
                    assertEquals(100000L, restored.state("42")?.automaticAttemptAt)
                    assertEquals(1, queue.activities().size)
                    clock.advance(Duration.ofHours(2).minusSeconds(1))
                    memory.tick()
                    assertEquals(1, queue.activities().size)
                    assertTrue(telegram.reads.isEmpty())
                    clock.advance(Duration.ofSeconds(1))
                    memory.tick()
                    await { queue.activities().any { it.status == "ready" } }
                    assertEquals(2, queue.activities().size)
                    assertEquals(1, telegram.reads.size)
                    assertEquals(107200L, restored.state("42")?.automaticAttemptAt)
                }
            }
        }
    }

    @Test
    fun `queued automatic work cancels without consuming an interval if its checkpoint already completed`() {
        val settings = settings()
        val telegram = telegram(1)
        val store = MemoryStore(directory, ResourceCatalog())
        val clock = AdvancingClock(Instant.ofEpochSecond(100000))
        val summaries = AtomicInteger()
        ReplyCoordinator(settings, telegram) { _, _ -> ReplyResult("Unused", 0, 0.0) }.use { queue ->
            queue.pauseQueue(true)
            MemoryModelServer().use { server ->
                coordinator(
                    settings,
                    telegram,
                    store,
                    server.start().runtime,
                    clock,
                    queue,
                    MemorySummarizer { _, _, _ ->
                        summaries.incrementAndGet()
                        "Unexpected summary"
                    },
                ).use { memory ->
                    memory.tick()
                    val previous = store.state("42") ?: error("Missing state")
                    assertEquals(0L, previous.automaticAttemptAt)
                    assertTrue(
                        store.commit(
                            previous,
                            store.read("42").documentRevision,
                            "Already collected",
                            previous.copy(
                                headId = 1,
                                observedId = 1,
                                backfillComplete = true,
                                dirty = false,
                                requested = false,
                                updatedAt = 100000,
                                recentUpdatedAt = 100000,
                                ownerMemoryVersion = 1,
                            ),
                        ),
                    )
                    queue.pauseQueue(false)
                    await { queue.activities().singleOrNull()?.status == "cancelled" }
                    assertEquals(0, summaries.get())
                    assertTrue(telegram.reads.isEmpty())
                    assertEquals(0L, store.state("42")?.automaticAttemptAt)
                    assertEquals("Already collected", store.read("42").summary)
                    memory.tick()
                    assertEquals(1, queue.activities().size)
                }
            }
        }
    }

    @Test
    fun `model availability and queue waiting never consume the automatic interval`() {
        val settings = settings()
        settings.update { it.copy(defaultModel = "unavailable") }
        val telegram = telegram(1)
        val store = MemoryStore(directory, ResourceCatalog())
        val clock = AdvancingClock(Instant.ofEpochSecond(100000))
        ReplyCoordinator(settings, telegram) { _, _ -> ReplyResult("Unused", 0, 0.0) }.use { queue ->
            queue.pauseQueue(true)
            MemoryModelServer().use { server ->
                coordinator(settings, telegram, store, server.start().runtime, clock, queue).use { memory ->
                    memory.tick()
                    assertEquals(MemoryStatus.WAITING_MODEL, memory.document("42").status)
                    assertEquals(0L, store.state("42")?.automaticAttemptAt)
                    assertTrue(queue.activities().isEmpty())
                    settings.update { it.copy(defaultModel = "gemma3:4b") }
                    memory.tick()
                    assertEquals(0L, store.state("42")?.automaticAttemptAt)
                    assertTrue(telegram.reads.isEmpty())
                    clock.advance(Duration.ofMinutes(5))
                    queue.pauseQueue(false)
                    await { queue.activities().singleOrNull()?.status == "ready" }
                    assertEquals(100300L, store.state("42")?.automaticAttemptAt)
                    assertEquals(1, telegram.reads.size)
                }
            }
        }
    }

    private fun settings(): SettingsStore = SettingsStore(directory).also {
        it.saveRule("42", ChatRule(enabled = true))
        it.update { current -> current.copy(memoryIntervalHours = 2) }
    }

    private fun telegram(messages: Int): MemoryTelegramGateway = MemoryTelegramGateway().also {
        it.chatIds = listOf("42")
        it.messages = IntStream.rangeClosed(1, messages).mapToObj { id ->
            ChatMessage("$id", "42", "Synthetic fact $id", false, id.toLong())
        }.toList()
    }

    private fun coordinator(
        settings: SettingsStore,
        telegram: MemoryTelegramGateway,
        store: MemoryStore,
        runtime: OllamaRuntime,
        clock: AdvancingClock,
        queue: MemoryWorkQueue = DirectMemoryWorkQueue(),
        summarizer: MemorySummarizer = MemorySummarizer { _, _, _ -> "Collected facts" },
    ): MemoryCoordinator {
        val automated = AutomatedReplyStore(directory)
        val processor = MemoryProcessor(
            telegram,
            store,
            MemoryBatchBuilder(automated),
            summarizer,
            clock,
            OwnerMemorySummarizer { _, old, _ -> old },
        )
        return MemoryCoordinator(
            store, settings, telegram, runtime, processor, ModelInference(), clock, automated, Provider { queue },
        )
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
