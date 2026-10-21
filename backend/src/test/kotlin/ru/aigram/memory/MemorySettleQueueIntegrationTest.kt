package ru.aigram.memory

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.ModelInference
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.configuration.testBeanProvider
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.events.ActivityKind
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
import kotlin.test.assertTrue

class MemorySettleQueueIntegrationTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `unsettled collection waits in one event without rereading or reporting false success then commits`() {
        val settings = SettingsStore(directory)
        settings.saveRule("42", ChatRule(enabled = true))
        val telegram = MemoryTelegramGateway()
        telegram.chatIds = listOf("42")
        telegram.messages = listOf(
            ChatMessage("9", "42", "Existing fact", false, 1),
            ChatMessage("10", "42", "New fact", false, 1000),
        )
        val clock = AdvancingClock(Instant.ofEpochSecond(1000))
        val automated = AutomatedReplyStore(directory)
        val store = MemoryStore(directory, ResourceCatalog())
        store.activate("100")
        store.observe("42", 9)
        val initial = store.state("42") ?: error("state")
        assertTrue(
            store.commit(
                initial,
                store.read("42").documentRevision,
                "Existing fact",
                initial.copy(headId = 9, backfillComplete = true, dirty = false, ownerMemoryVersion = 1),
            ),
        )
        store.observe("42", 10)
        val summaries = AtomicInteger()
        val processor = MemoryProcessor(
            telegram,
            store,
            MemoryBatchBuilder(automated),
            MemorySummarizer { _, _, _ ->
                summaries.incrementAndGet()
                "Updated facts"
            },
            clock,
            OwnerMemorySummarizer { _, old, _ -> old },
        )
        ReplyCoordinator(settings, telegram) { _, _ -> ReplyResult("Never sent", 0, 0.0) }.use { queue ->
            MemoryModelServer().use { server ->
                MemoryCoordinator(
                    store, settings, telegram, server.start().runtime, processor, ModelInference(), clock,
                    automated, testBeanProvider { queue },
                ).use { memory ->
                    memory.refresh("42", immediate = true)
                    await {
                        telegram.reads.isNotEmpty() && queue.activities().singleOrNull()?.let {
                            it.status == "queued" && it.phase == "waiting-resource" &&
                                queue.memoryQueuePosition(it.id) > 0
                        } == true
                    }
                    val first = queue.activities().single()
                    TimeUnit.MILLISECONDS.sleep(350)
                    assertEquals(1, telegram.reads.size)
                    assertEquals(1, queue.activities().size)
                    assertEquals(0, summaries.get())
                    assertEquals(9L, store.state("42")?.headId)
                    assertEquals(1, memory.document("42").collection.queuedChats)
                    assertEquals(1, memory.document("42").collection.queuePosition)
                    val previousGeneration = store.state("42")?.generation ?: error("state")
                    store.cancelCollection("42")
                    store.observe("42", 10, request = true)
                    assertEquals(previousGeneration + 1, store.state("42")?.generation)
                    queue.pauseQueue(true)
                    clock.advance(Duration.ofSeconds(11))
                    queue.pauseQueue(false)
                    await { queue.details(first.id).entry.status == "ready" }
                    assertEquals(1, summaries.get())
                    assertEquals(10L, store.state("42")?.headId)
                    assertEquals("Updated facts", store.read("42").facts())
                    assertEquals(first.id, queue.activities().single { it.kind == ActivityKind.MEMORY }.id)
                }
            }
        }
    }

    private fun await(condition: () -> Boolean) {
        assertTrue(
            IntStream.range(0, 1500).anyMatch {
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
