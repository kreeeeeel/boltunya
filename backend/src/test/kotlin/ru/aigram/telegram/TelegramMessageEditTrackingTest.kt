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
import ru.aigram.exception.impl.MessageEditConflictException
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
import ru.aigram.storage.WritingStyleStore
import java.nio.file.Path
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TelegramMessageEditTrackingTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `incoming edit invalidates only its chat preserves notes AI provenance and unrelated queued drafts`() {
        val clock = Clock.systemUTC()
        val settings = SettingsStore(directory)
        settings.saveRule("42", ChatRule(enabled = true))
        settings.saveRule("43", ChatRule(enabled = true))
        val telegram = MemoryTelegramGateway()
        val bank = MemoryStore(directory, ResourceCatalog())
        bank.activate("100")
        bank.observe("42", 123, manual = true)
        val state = bank.state("42") ?: error("Missing tracked state")
        assertTrue(
            bank.commit(
                state,
                bank.read("42").documentRevision,
                "Obsolete chat fact",
                state.copy(headId = 123),
                OwnerMemoryUpdate(bank.read(""), "Obsolete owner fact"),
            ),
        )
        bank.edit("42", "Manual chat note", bank.read("42").revision)
        bank.edit("", "Manual owner note", bank.read("").revision)
        bank.observe("43", 456)
        val unrelatedState = bank.state("43")
        val unrelatedDocument = bank.read("43")
        val automated = AutomatedReplyStore(directory)
        automated.record("42", "123")
        val processor = MemoryProcessor(
            telegram,
            bank,
            MemoryBatchBuilder(automated),
            MemorySummarizer { _, _, _ -> error("Editing must not start inference") },
            clock,
            OwnerMemorySummarizer { _, previous, _ -> previous },
        )
        val old = ChatMessage("123", "42", "Old content", true, 100, automated = true)
        val archive = MessageArchiveStore(directory, clock)
        archive.record("100", listOf(old))
        val writing = WritingStyleStore(directory)
        writing.update("42", listOf(old.copy(automated = false)))
        val unrelatedSample = ChatMessage("456", "43", "Other sample", true, 100)
        writing.update("43", listOf(unrelatedSample))
        val raw = TdApi.Message().apply {
            id = 123
            chatId = 42
            content = TdApi.MessageText(TdApi.FormattedText("Old content", emptyArray()), null, null)
        }
        val cache = TelegramCache().apply { ownId = 100 }
        cache.chats[42] = TdApi.Chat().apply { lastMessage = raw }
        val mapper = TelegramMessageMapper(TelegramMediaStore(directory) { error("No file downloads") })
        ReplyCoordinator(settings, telegram) { _, _ -> error("No reply generation") }.use { replies ->
            replies.pauseQueue(true)
            val affected = replies.enqueueDraft("42", "test") { ReplyResult("unused", 0, 0.0) }
            val unrelated = replies.enqueueDraft("43", "test") { ReplyResult("unused", 0, 0.0) }
            MemoryCoordinator(
                bank, settings, telegram, OllamaRuntime("http://127.0.0.1:1"), processor,
                ModelInference(), clock, automated, testBeanProvider { DirectMemoryWorkQueue() },
            ).use { memory ->
                val tracker = TelegramHistoryTracker(
                    archive,
                    cache,
                    mapper,
                    testBeanProvider { replies },
                    testBeanProvider { memory },
                    writing,
                )
                val replacement = TdApi.MessageText(TdApi.FormattedText("New content", emptyArray()), null, null)
                tracker.edited(TdApi.UpdateMessageContent(42, 123, replacement))

                assertEquals("New content", archive.find("100", "42", "123")?.text)
                assertTrue(archive.find("100", "42", "123")?.automated == true)
                assertEquals("New content", (cache.chats[42]?.lastMessage?.content as TdApi.MessageText).text.text)
                assertEquals("cancelled", replies.draft(affected.id).status)
                assertEquals("queued", replies.draft(unrelated.id).status)
                assertEquals("", bank.read("42").summary)
                assertEquals("", bank.read("").summary)
                assertEquals("Manual chat note", bank.read("42").notes)
                assertEquals("Manual owner note", bank.read("").notes)
                assertEquals(unrelatedState, bank.state("43"))
                assertEquals(unrelatedDocument, bank.read("43"))
                assertTrue(writing.update("42", emptyList()).isEmpty())
                assertEquals(listOf(unrelatedSample), writing.update("43", emptyList()))
                assertTrue(automated.contains("42", "123"))
                val changed = old.copy(text = "New content", editedAt = 101)
                assertTrue(writing.update("42", listOf(changed)).isEmpty())
                val revision = bank.read("42").documentRevision
                tracker.edited(TdApi.UpdateMessageContent(42, 123, replacement))
                tracker.edited(TdApi.UpdateMessageEdited(42, 123, 101, null))
                assertEquals(revision, bank.read("42").documentRevision)
                assertEquals(101L, archive.find("100", "42", "123")?.editedAt)
                assertEquals(changed, tracker.edited("100", raw, changed))
                assertFailsWith<MessageEditConflictException> { tracker.edited("101", raw, changed) }
                assertTrue(telegram.reads.isEmpty())
            }
        }
    }
}
