package ru.aigram.memory

import jakarta.inject.Provider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.ModelInference
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ActivityKind
import ru.aigram.domain.ChatMessage
import ru.aigram.domain.ChatRule
import ru.aigram.domain.ReplyResult
import ru.aigram.resources.ResourceCatalog
import ru.aigram.storage.AutomatedReplyStore
import ru.aigram.storage.SettingsStore
import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.TimeUnit
import java.util.stream.IntStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedMemoryQueueIntegrationTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `memory collection waits in the shared queue and cancellation preserves notes without reading history`() {
        val settings = SettingsStore(directory)
        settings.saveRule("42", ChatRule(enabled = true))
        val telegram = MemoryTelegramGateway()
        telegram.messages = listOf(ChatMessage("1", "42", "Confirmed fact", false, 1))
        val automated = AutomatedReplyStore(directory)
        val store = MemoryStore(directory, ResourceCatalog())
        store.activate("100")
        store.edit("42", "Manual note", store.read("42").revision)
        val processor = MemoryProcessor(
            telegram,
            store,
            MemoryBatchBuilder(automated),
            MemorySummarizer { _, _, _ -> "Collected facts" },
            Clock.systemUTC(),
            OwnerMemorySummarizer { _, old, _ -> old },
        )
        ReplyCoordinator(settings, telegram) { _, _ -> ReplyResult("Never sent", 0, 0.0) }.use { queue ->
            queue.pauseQueue(true)
            queue.enqueueDraft("43", "test") { ReplyResult("Only a draft", 0, 0.0) }
            MemoryModelServer().use { server ->
                MemoryCoordinator(
                    store, settings, telegram, server.start().runtime, processor, ModelInference(), Clock.systemUTC(),
                    automated, Provider { queue },
                ).use { memory ->
                    memory.refresh("42", immediate = true)
                    await { queue.activities().any { it.kind == ActivityKind.MEMORY } }
                    val first = queue.activities().single { it.kind == ActivityKind.MEMORY }
                    assertEquals("queued", first.status)
                    assertTrue(memory.document("42").collection.active)
                    assertEquals(2, memory.document("42").collection.queuePosition)
                    assertTrue(telegram.reads.isEmpty())
                    queue.cancel(first.id)
                    assertFalse(memory.document("42").collection.active)
                    assertEquals("Manual note", store.read("42").notes)
                    assertTrue(telegram.reads.isEmpty())
                    memory.refresh("42", immediate = true)
                    await { queue.activities().any { it.status == "queued" } }
                    queue.pauseQueue(false)
                    await {
                        store.read("42").summary == "Collected facts" &&
                            queue.activities().any { it.kind == ActivityKind.MEMORY && it.status == "ready" }
                    }
                    assertEquals("Manual note", store.read("42").notes)
                    assertTrue(queue.activities().any { it.kind == ActivityKind.MEMORY && it.status == "ready" })
                }
            }
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
