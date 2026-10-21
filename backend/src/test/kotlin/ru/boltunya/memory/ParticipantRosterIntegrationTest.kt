package ru.boltunya.memory

import jakarta.inject.Provider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.ai.ModelInference
import ru.boltunya.ai.OllamaRuntime
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.domain.ContextExclusionUpdate
import ru.boltunya.domain.GroupReplyMode
import ru.boltunya.resources.ResourceCatalog
import ru.boltunya.storage.AutomatedReplyStore
import ru.boltunya.storage.ContextExclusionStore
import ru.boltunya.storage.SettingsStore
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParticipantRosterIntegrationTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `collects observed and fetched group authors without fetching the member list or generating descriptions`() {
        val rule = ChatRule(enabled = true, groupReplies = GroupReplyMode.ALL)
        val settings = SettingsStore(directory)
        settings.saveRule("42", rule)
        val telegram = MemoryTelegramGateway()
        telegram.groupChats = setOf("42")
        val store = MemoryStore(directory, ResourceCatalog())
        val exclusions = ContextExclusionStore(directory)
        val roster = ParticipantRosterStore(directory, exclusions)
        val clock = Clock.fixed(Instant.ofEpochSecond(1000), ZoneOffset.UTC)
        val automated = AutomatedReplyStore(directory)
        val processor = MemoryProcessor(
            telegram,
            store,
            MemoryBatchBuilder(automated),
            MemorySummarizer { _, _, _ -> "Confirmed facts" },
            clock,
            OwnerMemorySummarizer { _, old, _ -> old },
            exclusions,
            roster,
        )
        MemoryCoordinator(
            store, settings, telegram,
            OllamaRuntime(
                "http://127.0.0.1:1",
            ),
            processor, ModelInference(), clock, automated,
            roster = roster, exclusions = exclusions,
            queueProvider = Provider { DirectMemoryWorkQueue() },
        ).use { coordinator ->
            val observed = message(1, "Anna", "anna_example")
            coordinator.observe(observed)
            assertEquals(listOf("user:1"), roster.context("100", "42").map { it.id })
            assertTrue(telegram.reads.isEmpty())
            val historical = message(2, "Mark", "mark_example")
            telegram.messages = listOf(historical)
            assertTrue(processor.process(store.state("42") ?: error("Missing memory state"), "test", true))
            assertEquals(listOf("user:2", "user:1"), roster.context("100", "42").map { it.id })
            val context = coordinator.context(rule, listOf(historical))
            assertEquals(listOf("mark_example", "anna_example"), context.participants.map { it.username })
            assertTrue(coordinator.context(rule.copy(useMemory = false), listOf(historical)).participants.isEmpty())
            exclusions.update("100", "42", "2", ContextExclusionUpdate())
            assertEquals(listOf("user:1"), coordinator.context(rule, listOf(historical)).participants.map { it.id })
            assertTrue(roster.context("101", "42").isEmpty())
        }
    }

    private fun message(id: Int, name: String, username: String) = ChatMessage(
        "$id", "42", "Message text", false, id.toLong(), group = true,
        senderName = name, senderId = "user:$id", senderUsername = username,
    )
}
