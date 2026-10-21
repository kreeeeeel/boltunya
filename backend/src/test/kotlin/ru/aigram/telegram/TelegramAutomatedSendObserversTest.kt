package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.ModelInference
import ru.aigram.ai.OllamaRuntime
import ru.aigram.configuration.testBeanProvider
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.memory.MemorySpeaker
import ru.aigram.domain.telegram.TelegramStatus
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
import ru.aigram.storage.CredentialStore
import ru.aigram.storage.SettingsStore
import ru.aigram.storage.WritingStyleStore
import java.nio.file.Path
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TelegramAutomatedSendObserversTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `native pending and success provenance exists before memory and owner writing style observers run`() {
        val clock = Clock.systemUTC()
        val cache = TelegramCache().apply { ownId = 100 }
        val connection = TelegramConnectionState(CredentialStore(directory)).apply {
            status = TelegramStatus(state = "ready")
            version = 7
        }
        val automated = AutomatedReplyStore(directory)
        val registry = TelegramAutomatedSendRegistry(connection, cache, automated, clock)
        val settings = SettingsStore(directory)
        settings.saveRule("42", ChatRule(enabled = true))
        val bank = MemoryStore(directory, ResourceCatalog())
        val telegram = MemoryTelegramGateway()
        val batches = MemoryBatchBuilder(automated)
        val processor = MemoryProcessor(
            telegram,
            bank,
            batches,
            MemorySummarizer { _, _, _ -> error("observing delivery must never start inference") },
            clock,
            OwnerMemorySummarizer { _, previous, _ -> previous },
        )
        val mapper = TelegramMessageMapper(TelegramMediaStore(directory) { error("no media downloads") })
        val writing = WritingStyleStore(directory)
        MemoryCoordinator(
            bank, settings, telegram, OllamaRuntime("http://127.0.0.1:1"), processor,
            ModelInference(), clock, automated, testBeanProvider { DirectMemoryWorkQueue() },
        ).use { memory ->
            val send = registry.begin(42)
            val pending = message(-1, send.sendingId)
            val pendingAutomated = registry.pending(pending)
            assertTrue(pendingAutomated)
            val mappedPending = mapper.map(pending).copy(automated = pendingAutomated)
            memory.observe(mappedPending)
            assertTrue(writing.update("42", listOf(mappedPending)).isEmpty())
            assertNull(bank.state("42"))

            val delivered = message(202)
            val deliveredAutomated = registry.succeeded(pending.id, delivered)
            assertTrue(deliveredAutomated)
            assertTrue(automated.contains("42", "202"))
            val mappedDelivered = mapper.map(delivered).copy(automated = deliveredAutomated)
            memory.observe(mappedDelivered)
            assertTrue(writing.update("42", listOf(mappedDelivered)).isEmpty())
            assertNull(bank.state("42"))

            // The same words sent manually by the owner are still genuine owner data.
            val manual = message(203)
            assertFalse(registry.pending(manual))
            val mappedManual = mapper.map(manual)
            memory.observe(mappedManual)
            assertEquals(203L, bank.state("42")?.observedId)
            assertEquals(listOf("203"), writing.update("42", listOf(mappedManual)).stream().map { it.id }.toList())
            val sources = batches.select(listOf(mappedDelivered.copy(automated = false), mappedManual)).sources
            assertEquals(
                listOf(MemorySpeaker.ASSISTANT, MemorySpeaker.OWNER),
                sources.stream().map { it.speaker }.toList(),
            )
        }
    }

    private fun message(id: Long, sendingId: Int? = null): TdApi.Message = TdApi.Message().apply {
        this.id = id
        chatId = 42
        date = 100
        isOutgoing = true
        senderId = TdApi.MessageSenderUser(100)
        sendingState = sendingId?.let { TdApi.MessageSendingStatePending(it) }
        content = TdApi.MessageText(TdApi.FormattedText("Identical synthetic words", emptyArray()), null, null)
    }
}
