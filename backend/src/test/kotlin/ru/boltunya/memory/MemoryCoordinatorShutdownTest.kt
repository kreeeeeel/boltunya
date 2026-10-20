package ru.boltunya.memory

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.ai.ModelInference
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.resources.ResourceCatalog
import ru.boltunya.storage.AutomatedReplyStore
import ru.boltunya.storage.SettingsStore
import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemoryCoordinatorShutdownTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `close interrupts collection and waits for the worker to finish cleanup`() {
        val settings = SettingsStore(directory)
        settings.saveRule("42", ChatRule(enabled = true))
        val telegram = MemoryTelegramGateway()
        telegram.messages = listOf(ChatMessage("1", "42", "Context", false, 1))
        val automated = AutomatedReplyStore(directory)
        val store = MemoryStore(directory, ResourceCatalog())
        val started = CountDownLatch(1)
        val cleanupStarted = CountDownLatch(1)
        val releaseCleanup = CountDownLatch(1)
        val cleaned = CountDownLatch(1)
        val processor = MemoryProcessor(
            telegram,
            store,
            MemoryBatchBuilder(automated),
            MemorySummarizer { _, _, _ ->
                started.countDown()
                try {
                    CountDownLatch(1).await()
                    "Summary"
                } finally {
                    cleanupStarted.countDown()
                    releaseCleanup.await(3, TimeUnit.SECONDS)
                    cleaned.countDown()
                }
            },
            Clock.systemUTC(),
            OwnerMemorySummarizer { _, existing, _ -> existing },
        )
        MemoryModelServer().use { server ->
            MemoryCoordinator(
                store,
                settings,
                telegram,
                server.start().runtime,
                processor,
                ModelInference(),
                Clock.systemUTC(),
                automated,
            ).use { coordinator ->
                coordinator.refresh("42", immediate = true)
                assertTrue(started.await(3, TimeUnit.SECONDS))
                val closing = CompletableFuture.runAsync { coordinator.close() }
                try {
                    assertTrue(cleanupStarted.await(3, TimeUnit.SECONDS))
                    assertFalse(closing.isDone, "close must wait until the worker releases its resources")
                } finally {
                    releaseCleanup.countDown()
                    closing.get(3, TimeUnit.SECONDS)
                }
                assertTrue(cleaned.await(0, TimeUnit.SECONDS))
                assertFalse(store.state("42")?.backfillComplete == true)
            }
        }
    }
}
