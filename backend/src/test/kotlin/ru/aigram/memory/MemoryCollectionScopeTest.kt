package ru.aigram.memory

import jakarta.inject.Provider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.ModelInference
import ru.aigram.ai.OllamaRuntime
import ru.aigram.domain.ChatMessage
import ru.aigram.domain.ChatRule
import ru.aigram.domain.GroupReplyMode
import ru.aigram.domain.MemoryCollectionScope
import ru.aigram.resources.ResourceCatalog
import ru.aigram.storage.AutomatedReplyStore
import ru.aigram.storage.SettingsStore
import java.nio.file.Path
import java.time.Clock
import java.util.stream.IntStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemoryCollectionScopeTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `default collection scans only AI enabled private chats and groups with replies enabled`() {
        val settings = settings()
        val telegram = telegram()
        val store = MemoryStore(directory, ResourceCatalog())
        assertEquals(MemoryCollectionScope.AI_ENABLED, settings.get().memoryCollectionScope)
        MemoryModelServer().use { server ->
            coordinator(settings, telegram, store, server.start().runtime).use { memory ->
                memory.refresh()
                IntStream.range(0, 8).forEach { memory.tick() }
                assertEquals(setOf("42", "46"), store.states().map { it.chatId }.toSet())
                assertEquals(setOf("42", "46"), telegram.reads.map { it.first }.toSet())
                assertTrue(store.state("42")?.backfillComplete == true)
                assertTrue(store.state("46")?.backfillComplete == true)
            }
        }
    }

    @Test
    fun `all allowed includes disabled private chats and groups while excluding opted out chats and channels`() {
        val settings = settings()
        settings.update { it.copy(memoryCollectionScope = MemoryCollectionScope.ALL_ALLOWED) }
        val telegram = telegram()
        val store = MemoryStore(directory, ResourceCatalog())
        MemoryModelServer().use { server ->
            coordinator(settings, telegram, store, server.start().runtime).use { memory ->
                memory.refresh()
                IntStream.range(0, 14).forEach { memory.tick() }
                val expected = setOf("42", "43", "44", "45", "46")
                assertEquals(expected, store.states().map { it.chatId }.toSet())
                assertEquals(expected, telegram.reads.map { it.first }.toSet())
                assertTrue(store.states().all { it.backfillComplete })
                assertFalse(telegram.reads.any { it.first == "47" || it.first == "48" })
            }
        }
    }

    @Test
    fun `narrowing scope does not resume previously requested disabled chats`() {
        val settings = settings()
        settings.update { it.copy(memoryCollectionScope = MemoryCollectionScope.ALL_ALLOWED) }
        val telegram = telegram()
        val store = MemoryStore(directory, ResourceCatalog())
        MemoryModelServer().use { server ->
            coordinator(settings, telegram, store, server.start().runtime).use { memory ->
                memory.refresh()
                assertTrue(store.state("43")?.requested == true)
                assertTrue(store.state("44")?.requested == true)
                settings.update { it.copy(memoryCollectionScope = MemoryCollectionScope.AI_ENABLED) }
                memory.refresh()
                IntStream.range(0, 10).forEach { memory.tick() }
                assertEquals(setOf("42", "46"), telegram.reads.map { it.first }.toSet())
                assertEquals(0L, store.state("43")?.processedMessages)
                assertEquals(0L, store.state("44")?.processedMessages)
            }
        }
    }

    @Test
    fun `broadening scope discovers disabled chats without reprocessing completed eligible history`() {
        val settings = settings()
        val telegram = telegram()
        val store = MemoryStore(directory, ResourceCatalog())
        MemoryModelServer().use { server ->
            coordinator(settings, telegram, store, server.start().runtime).use { memory ->
                memory.refresh()
                IntStream.range(0, 8).forEach { memory.tick() }
                assertEquals(setOf("42", "46"), telegram.reads.map { it.first }.toSet())
                telegram.reads.clear()
                settings.update { it.copy(memoryCollectionScope = MemoryCollectionScope.ALL_ALLOWED) }
                memory.refresh()
                IntStream.range(0, 10).forEach { memory.tick() }
                assertEquals(setOf("43", "44", "45"), telegram.reads.map { it.first }.toSet())
                assertFalse(telegram.reads.any { it.first == "47" || it.first == "48" })
            }
        }
    }

    private fun settings(): SettingsStore {
        val settings = SettingsStore(directory)
        settings.saveRule("42", ChatRule(enabled = true))
        settings.saveRule("43", ChatRule(enabled = false))
        settings.saveRule("44", ChatRule(enabled = false, groupReplies = GroupReplyMode.DISABLED))
        settings.saveRule("45", ChatRule(enabled = true, groupReplies = GroupReplyMode.DISABLED))
        settings.saveRule("46", ChatRule(enabled = true, groupReplies = GroupReplyMode.MENTIONS))
        settings.saveRule("47", ChatRule(enabled = true, useMemory = false))
        settings.saveRule("48", ChatRule(enabled = true, groupReplies = GroupReplyMode.ALL))
        return settings
    }

    private fun telegram(): MemoryTelegramGateway = MemoryTelegramGateway().also { telegram ->
        telegram.chatIds = (42..48).map(Int::toString)
        telegram.groupChats = setOf("44", "45", "46")
        telegram.channelChats = setOf("48")
        telegram.messages = telegram.chatIds.map { id ->
            ChatMessage("1", id, "A synthetic fact", false, 1, group = id in telegram.groupChats)
        }
    }

    private fun coordinator(
        settings: SettingsStore,
        telegram: MemoryTelegramGateway,
        store: MemoryStore,
        runtime: OllamaRuntime,
    ): MemoryCoordinator {
        val automated = AutomatedReplyStore(directory)
        val inference = ModelInference()
        val clock = Clock.systemUTC()
        val processor = MemoryProcessor(
            telegram,
            store,
            MemoryBatchBuilder(automated),
            MemorySummarizer { _, _, _ -> "Collected facts" },
            clock,
            OwnerMemorySummarizer { _, existing, _ -> existing },
        )
        return MemoryCoordinator(
            store, settings, telegram, runtime, processor, inference, clock, automated,
            Provider { DirectMemoryWorkQueue() },
        )
    }
}
