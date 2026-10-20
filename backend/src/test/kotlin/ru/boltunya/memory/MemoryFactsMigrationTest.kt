package ru.boltunya.memory

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.ai.ModelInference
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.resources.ResourceCatalog
import ru.boltunya.storage.AutomatedReplyStore
import ru.boltunya.storage.SettingsStore
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemoryFactsMigrationTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `legacy documents stay readable but are not factual context and editing never promotes their style`() {
        val states = legacy("42", "43")
        val path = directory.resolve("memory/100/42/MEMORY.md")
        val original = Files.readString(path)
        val profile = Files.readString(directory.resolve("memory/100/PROFILE.md"))
        val store = MemoryStore(directory, ResourceCatalog())
        store.activate("100")
        assertEquals("Ручная общая заметка  \n", store.read("").notes)
        assertTrue(store.read("").summary.contains("Манера общения"))
        assertEquals("", store.read("").facts())
        assertEquals("", store.read("42").facts())
        val revision = store.read("42").documentRevision
        store.observe("42", 99)
        val migrated = store.state("42") ?: error("state")
        assertEquals(0L, migrated.headId)
        assertEquals(0L, migrated.backfillBeforeId)
        assertFalse(migrated.backfillComplete)
        assertTrue(migrated.dirty)
        assertEquals(states.getValue("42").generation + 1, migrated.generation)
        assertEquals(states.getValue("43"), store.state("43"))
        assertEquals(original, Files.readString(path))
        assertEquals(profile, Files.readString(directory.resolve("memory/100/PROFILE.md")))
        assertFalse(store.commit(states.getValue("42"), revision, "Stale result", states.getValue("42")))
        store.observe("42", 99)
        assertEquals(migrated, store.state("42"))
        store.edit("42", "Новая ручная заметка  \n", store.read("42").revision)
        assertEquals("Новая ручная заметка  \n", store.read("42").notes)
        assertEquals(0, store.read("42").factsVersion)
        assertEquals("", store.read("42").facts())
        assertTrue(store.read("42").summary.contains("Манера общения"))
    }

    @Test
    fun `rebuild never feeds legacy writing habits to summarizers and persists factual provenance across restart`() {
        legacy("42")
        val store = MemoryStore(directory, ResourceCatalog())
        store.activate("100")
        store.observe("42", 100)
        val telegram = MemoryTelegramGateway()
        telegram.messages = listOf(ChatMessage("100", "42", "Я теперь живу в Казани и люблю чай", true, 1))
        val previousChat = mutableListOf<String>()
        val previousOwner = mutableListOf<String>()
        val processor = MemoryProcessor(
            telegram,
            store,
            MemoryBatchBuilder(AutomatedReplyStore(directory)),
            MemorySummarizer { _, existing, _ ->
                previousChat.add(existing)
                "## События и договорённости\nВладелец переехал в Казань [id 100]."
            },
            Clock.fixed(Instant.ofEpochSecond(100000), ZoneOffset.UTC),
            OwnerMemorySummarizer { _, existing, _ ->
                previousOwner.add(existing)
                "## Факты обо мне\nЖивёт в Казани. Любит чай."
            },
        )
        assertTrue(processor.process(store.state("42") ?: error("state"), "test", true))
        assertEquals(listOf(""), previousChat)
        assertEquals(listOf(""), previousOwner)
        assertEquals(FACTUAL_MEMORY_VERSION, store.state("42")?.factsVersion)
        assertTrue(store.read("42").facts().contains("Казань"))
        assertTrue(store.read("").facts().contains("Казани"))
        val restored = MemoryStore(directory, ResourceCatalog())
        restored.activate("100")
        assertEquals(store.read("42").facts(), restored.read("42").facts())
        assertEquals(store.read("").facts(), restored.read("").facts())
        assertEquals("Ручная общая заметка  \n", restored.read("").notes)
        assertEquals("Ручная заметка 42  \n", restored.read("42").notes)
        val state = restored.state("42")
        restored.observe("42", 100)
        assertEquals(state, restored.state("42"))
    }

    @Test
    fun `startup migrates eligible tracked chats only and excludes old owner style from reply context`() {
        val old = legacy("42", "43", "44", "45")
        val settings = SettingsStore(directory)
        settings.saveRule("42", ChatRule(enabled = true))
        settings.saveRule("44", ChatRule(enabled = true, useMemory = false))
        settings.saveRule("45", ChatRule(enabled = true))
        val telegram = MemoryTelegramGateway()
        telegram.chatIds = listOf("42", "43", "44")
        telegram.messages = listOf(ChatMessage("100", "42", "Встречаемся в субботу", false, 1))
        val store = MemoryStore(directory, ResourceCatalog())
        val clock = Clock.fixed(Instant.ofEpochSecond(100000), ZoneOffset.UTC)
        val automated = AutomatedReplyStore(directory)
        val processor = MemoryProcessor(
            telegram,
            store,
            MemoryBatchBuilder(automated),
            MemorySummarizer { _, _, _ -> "Встреча в субботу [id 100]." },
            clock,
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
                clock,
                automated,
            ).use { coordinator ->
                val context = coordinator.context(ChatRule(useMemory = false), telegram.messages)
                assertEquals("Ручная общая заметка  \n", context.globalNotes)
                assertEquals("", context.ownerSummary)
                assertEquals("", context.summary)
                coordinator.tick()
                assertEquals(listOf("42"), telegram.reads.map { it.first })
                assertEquals(FACTUAL_MEMORY_VERSION, store.state("42")?.factsVersion)
                listOf("43", "44", "45").stream().forEach { id ->
                    assertEquals(old.getValue(id), store.state(id))
                    assertEquals("", store.read(id).facts())
                }
                val factual = coordinator.context(ChatRule(enabled = true), telegram.messages)
                assertEquals("Встреча в субботу [id 100].", factual.summary)
                assertEquals("", factual.ownerSummary)
            }
        }
    }

    private fun legacy(vararg chatIds: String): Map<String, ChatMemoryState> {
        val root = directory.resolve("memory")
        Files.createDirectories(root.resolve("100"))
        val summary = "## Факты\nЛюбит чай.\n## Манера общения\nПишет со строчной буквы, с матом и эмодзи."
        Files.writeString(root.resolve("MEMORY.md"), document("Ручная общая заметка  \n", summary))
        Files.writeString(root.resolve("100/PROFILE.md"), summary)
        val states = chatIds.toList().stream().map { id ->
            Files.createDirectories(root.resolve("100/$id"))
            Files.writeString(root.resolve("100/$id/MEMORY.md"), document("Ручная заметка $id  \n", summary))
            ChatMemoryState(
                id,
                accountId = "100",
                headId = 99,
                backfillBeforeId = 1,
                backfillComplete = true,
                observedId = 99,
                dirty = false,
                ownerMemoryVersion = 1,
                processedMessages = 99,
                generation = 3,
            )
        }.toList().associateBy { it.chatId }
        Files.writeString(root.resolve("100/state.json"), Json.encodeToString(states))
        return states
    }

    private fun document(notes: String, summary: String): String =
        notes + "\n\n<!-- boltunya:auto:start -->\n" + summary + "\n<!-- boltunya:auto:end -->\n"
}
