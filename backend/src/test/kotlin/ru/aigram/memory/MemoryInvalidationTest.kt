package ru.aigram.memory

import jakarta.inject.Provider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.ModelInference
import ru.aigram.ai.OllamaRuntime
import ru.aigram.domain.ChatMessage
import ru.aigram.domain.ChatRule
import ru.aigram.domain.ContextExclusionUpdate
import ru.aigram.resources.ResourceCatalog
import ru.aigram.storage.AutomatedReplyStore
import ru.aigram.storage.ContextExclusionStore
import ru.aigram.storage.SettingsStore
import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.TimeUnit
import java.util.stream.IntStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemoryInvalidationTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `persisted manual request cannot scan a disabled chat until the owner explicitly resumes it`() {
        val telegram = MemoryTelegramGateway()
        telegram.messages = listOf(
            ChatMessage("1", "43", "Older fact", false, 1),
            ChatMessage("11", "43", "Recent fact", false, 11),
        )
        val settings = SettingsStore(directory)
        val store = MemoryStore(directory, ResourceCatalog())
        store.activate("100")
        store.observe("43", 11, manual = true, request = true)
        val previous = store.state("43") ?: error("Missing memory state")
        assertTrue(
            store.commit(
                previous,
                store.read("43").documentRevision,
                "Earlier facts",
                previous.copy(
                    headId = 10,
                    backfillBeforeId = 5,
                    requested = true,
                    backfillComplete = false,
                    ownerMemoryVersion = 1,
                ),
            ),
        )
        val automated = AutomatedReplyStore(directory)
        val processor = MemoryProcessor(
            telegram,
            store,
            MemoryBatchBuilder(automated),
            MemorySummarizer { _, _, _ -> "Collected facts" },
            Clock.systemUTC(),
            OwnerMemorySummarizer { _, old, _ -> old },
        )
        MemoryModelServer().use { server ->
            MemoryCoordinator(
                store, settings, telegram, server.start().runtime, processor, ModelInference(), Clock.systemUTC(),
                automated, Provider { DirectMemoryWorkQueue() },
            ).use { memory ->
                memory.tick()
                assertTrue(telegram.reads.isEmpty())
                assertEquals(5L, store.state("43")?.backfillBeforeId)
                memory.refresh("43")
                memory.tick()
                assertTrue(
                    IntStream.range(0, 300).anyMatch {
                        if (store.state("43")?.backfillComplete == true) {
                            true
                        } else {
                            TimeUnit.MILLISECONDS.sleep(10)
                            false
                        }
                    },
                )
                assertTrue(telegram.reads.any { it.second == 5L })
            }
        }
    }

    @Test
    fun `excluding a message clears disabled chat auto facts without deleting notes or starting collection`() {
        val telegram = MemoryTelegramGateway()
        val settings = SettingsStore(directory)
        val store = MemoryStore(directory, ResourceCatalog())
        val automated = AutomatedReplyStore(directory)
        store.activate("100")
        store.observe("43", 2, manual = true)
        store.edit("43", "Owner's manual note", store.read("43").revision)
        val previous = store.state("43") ?: error("Missing memory state")
        assertTrue(
            store.commit(
                previous,
                store.read("43").documentRevision,
                "Fact from excluded message",
                previous.copy(headId = 2, backfillComplete = true, requested = false, dirty = false),
            ),
        )
        val exclusions = ContextExclusionStore(directory)
        exclusions.update("100", "43", "2", ContextExclusionUpdate())
        val processor = MemoryProcessor(
            telegram,
            store,
            MemoryBatchBuilder(automated),
            MemorySummarizer { _, _, _ -> throw AssertionError("Disabled chat must not be summarized") },
            Clock.systemUTC(),
            OwnerMemorySummarizer { _, old, _ -> old },
            exclusions,
        )
        MemoryCoordinator(
            store, settings, telegram, OllamaRuntime("http://127.0.0.1:1"), processor, ModelInference(),
            Clock.systemUTC(), automated, Provider { DirectMemoryWorkQueue() }, exclusions = exclusions,
        ).use { memory ->
            memory.invalidateContext("43")
            memory.refresh(rebuild = true)
            memory.tick()
            assertEquals("", store.read("43").summary)
            assertEquals("Owner's manual note", store.read("43").notes)
            assertEquals(false, store.state("43")?.requested)
            val context = memory.context(ChatRule(), listOf(ChatMessage("3", "43", "New message", false, 3)))
            assertEquals("", context.summary)
            assertTrue(telegram.reads.isEmpty())
        }
    }
}
