package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.ModelInference
import ru.aigram.ai.OllamaRuntime
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.configuration.testBeanProvider
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.memory.OwnerMemoryUpdate
import ru.aigram.memory.DirectMemoryWorkQueue
import ru.aigram.memory.MemoryBatchBuilder
import ru.aigram.memory.MemoryCoordinator
import ru.aigram.memory.MemoryProcessor
import ru.aigram.memory.MemoryStore
import ru.aigram.memory.MemorySummarizer
import ru.aigram.memory.MemoryTelegramGateway
import ru.aigram.memory.OwnerMemorySummarizer
import ru.aigram.resources.ResourceCatalog
import ru.aigram.storage.AutomatedReplyStore
import ru.aigram.storage.MessageArchiveStore
import ru.aigram.storage.SettingsStore
import java.nio.file.Path
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TelegramHistoryTrackerTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `permanent deletion invalidates only affected facts and preserves unrelated collection progress and notes`() {
        val clock = Clock.systemUTC()
        val settings = SettingsStore(directory)
        settings.saveRule("42", ChatRule(enabled = true))
        settings.saveRule("43", ChatRule(enabled = true))
        val telegram = MemoryTelegramGateway()
        val bank = MemoryStore(directory, ResourceCatalog())
        bank.activate("100")
        bank.observe("42", 123, manual = true)
        val state = bank.state("42") ?: error("Missing memory state")
        assertTrue(
            bank.commit(
                state,
                bank.read("42").documentRevision,
                "Obsolete fact",
                state.copy(headId = 123),
                OwnerMemoryUpdate(bank.read(""), "Obsolete owner fact"),
            ),
        )
        bank.edit("42", "Manual note", bank.read("42").revision)
        bank.edit("", "Manual owner note", bank.read("").revision)
        bank.observe("43", 456)
        val unrelated = bank.state("43") ?: error("Missing unrelated memory state")
        assertTrue(
            bank.commit(
                unrelated,
                bank.read("43").documentRevision,
                "Unrelated fact",
                unrelated.copy(
                    headId = 456,
                    backfillBeforeId = 100,
                    backfillComplete = true,
                    dirty = false,
                    requested = false,
                    ownerMemoryVersion = 1,
                    updatedAt = 1234,
                    recentUpdatedAt = 1234,
                ),
            ),
        )
        val unrelatedState = bank.state("43")
        val unrelatedDocument = bank.read("43")
        val automated = AutomatedReplyStore(directory)
        val processor = MemoryProcessor(
            telegram,
            bank,
            MemoryBatchBuilder(automated),
            MemorySummarizer { _, _, _ -> error("Deletion must not generate immediately") },
            clock,
            OwnerMemorySummarizer { _, previous, _ -> previous },
        )
        val archive = MessageArchiveStore(directory, clock)
        archive.record("100", listOf(ChatMessage("123", "42", "Retained original", false, 100)))
        val cache = TelegramCache().apply { ownId = 100 }
        cache.chats[42] = TdApi.Chat().apply { lastMessage = TdApi.Message().apply { id = 123 } }
        val mapper = TelegramMessageMapper(TelegramMediaStore(directory) { error("No media downloads") })
        ReplyCoordinator(settings, telegram) { _, _ -> ReplyResult("unused", 0, 0.0) }.use { replies ->
            MemoryCoordinator(
                bank, settings, telegram, OllamaRuntime("http://127.0.0.1:1"), processor,
                ModelInference(), clock, automated, testBeanProvider { DirectMemoryWorkQueue() },
            ).use { memory ->
                val tracker =
                    TelegramHistoryTracker(
                        archive,
                        cache,
                        mapper,
                        testBeanProvider {
                            replies
                        },
                        testBeanProvider { memory },
                    )
                val ownerBefore = bank.read("")
                tracker.deleted("100", 44, listOf(124))
                assertEquals(ownerBefore, bank.read(""))
                tracker.deleted("100", 42, listOf(123))
                val deleted = archive.find("100", "42", "123") ?: error("Missing tombstone")
                assertTrue(deleted.deleted)
                assertEquals("Retained original", deleted.text)
                assertNull(cache.chats[42]?.lastMessage)
                assertEquals("", bank.read("42").summary)
                assertEquals("", bank.read("").summary)
                assertEquals("Manual note", bank.read("42").notes)
                assertEquals("Manual owner note", bank.read("").notes)
                assertEquals(unrelatedState, bank.state("43"))
                assertEquals(unrelatedDocument, bank.read("43"))
                assertTrue(telegram.reads.isEmpty())
                val revision = bank.read("42").documentRevision
                tracker.deleted("100", 42, listOf(123))
                assertEquals(revision, bank.read("42").documentRevision)
                assertEquals(deleted, archive.find("100", "42", "123"))
            }
        }
    }
}
