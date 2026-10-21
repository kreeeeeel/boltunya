package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.telegram.TelegramStatus
import ru.aigram.memory.AdvancingClock
import ru.aigram.storage.AutomatedReplyStore
import ru.aigram.storage.CredentialStore
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TelegramAutomatedSendRegistryTest {
    @TempDir lateinit var directory: Path
    private val cache = TelegramCache().apply { ownId = 100 }
    private val clock = AdvancingClock(Instant.ofEpochSecond(1000))

    @Test
    fun `registers an exact sending id before pending updates and retains final provenance before observers`() {
        val connection = connection()
        val automated = AutomatedReplyStore(directory)
        val registry = TelegramAutomatedSendRegistry(connection, cache, automated, clock)
        val send = registry.begin(42)
        val other = registry.begin(43)
        assertNotEquals(0, send.sendingId)
        assertNotEquals(send.sendingId, other.sendingId)
        assertEquals("100", send.ownerId)
        assertEquals(7L, send.version)
        assertEquals(42L, send.chatId)

        val pending = message(-1, 42, send.sendingId)
        assertTrue(registry.pending(pending))
        val delivered = message(9007199254740993L, 42)
        assertTrue(registry.succeeded(pending.id, delivered))
        assertTrue(automated.contains("42", delivered.id.toString()))
        registry.succeeded(pending.id, delivered)
        registry.completed(send, delivered)
        assertTrue(AutomatedReplyStore(directory).contains("42", delivered.id.toString()))
        assertFalse(automated.contains("43", delivered.id.toString()))
    }

    @Test
    fun `completed response can arrive before pending or succeeded updates without losing provenance`() {
        val automated = AutomatedReplyStore(directory)
        val registry = TelegramAutomatedSendRegistry(connection(), cache, automated, clock)
        val send = registry.begin(42)
        val delivered = message(901, 42)

        registry.completed(send, delivered)

        assertTrue(automated.contains("42", "901"))
        assertFalse(automated.contains("42", "902"))
    }

    @Test
    fun `reordered final and late pending updates use scoped receipts rather than historical global IDs`() {
        val connection = connection()
        val automated = AutomatedReplyStore(directory)
        val registry = TelegramAutomatedSendRegistry(connection, cache, automated, clock)
        val send = registry.begin(42)
        val delivered = message(901, 42)
        registry.completed(send, delivered)

        assertTrue(registry.known(delivered))
        assertFalse(registry.known(message(901, 43)))
        assertFalse(registry.known(message(901, 42).apply { isOutgoing = false }))
        assertTrue(registry.pending(message(-1, 42, send.sendingId)))
        assertTrue(registry.succeeded(-1, delivered))

        cache.ownId = 200
        assertTrue(automated.contains("42", "901"))
        assertFalse(registry.known(delivered), "a legacy global ID must not mark another account's message")
        cache.ownId = 100
        connection.version += 1
        assertFalse(registry.known(delivered), "a previous connection receipt must not suppress manual messages")
    }

    @Test
    fun `manual same-text outgoing and incoming messages never acquire automation provenance`() {
        val automated = AutomatedReplyStore(directory)
        val registry = TelegramAutomatedSendRegistry(connection(), cache, automated, clock)
        val send = registry.begin(42)
        val text = "Identical synthetic content"
        val pending = message(-1, 42, send.sendingId, text)
        assertTrue(registry.pending(pending))
        assertFalse(registry.pending(message(-2, 42, 0, text)))
        assertFalse(registry.pending(message(-3, 42, -1, text)))
        assertFalse(registry.pending(message(-4, 43, send.sendingId, text)))
        assertFalse(registry.pending(message(-5, 42, send.sendingId, text).apply { isOutgoing = false }))
        assertFalse(registry.succeeded(-2, message(902, 42, text = text)))
        assertFalse(automated.contains("42", "902"))
        assertFalse(automated.contains("43", "902"))
        assertTrue(registry.succeeded(-1, message(901, 42, text = text)))
        assertTrue(automated.contains("42", "901"))
    }

    @Test
    fun `account switches and connection revisions cannot reuse pending registrations`() {
        val connection = connection()
        val automated = AutomatedReplyStore(directory)
        val registry = TelegramAutomatedSendRegistry(connection, cache, automated, clock)
        val first = registry.begin(42)
        assertTrue(registry.pending(message(-1, 42, first.sendingId)))
        cache.ownId = 200
        assertFalse(registry.pending(message(-2, 42, first.sendingId)))
        assertFalse(registry.succeeded(-1, message(901, 42)))
        registry.completed(first, message(902, 42))
        assertFalse(automated.contains("42", "901"))
        assertFalse(automated.contains("42", "902"))

        val second = registry.begin(42)
        assertEquals("200", second.ownerId)
        connection.version += 1
        assertFalse(registry.pending(message(-3, 42, second.sendingId)))
        registry.completed(second, message(903, 42))
        assertFalse(automated.contains("42", "903"))
    }

    @Test
    fun `rejected failed cleared and expired registrations never suppress later manual updates`() {
        val automated = AutomatedReplyStore(directory)
        val registry = TelegramAutomatedSendRegistry(connection(), cache, automated, clock)
        val rejected = registry.begin(42)
        registry.rejected(rejected)
        assertFalse(registry.pending(message(-1, 42, rejected.sendingId)))

        val failed = registry.begin(42)
        assertTrue(registry.pending(message(-2, 42, failed.sendingId)))
        registry.failed(42, -2)
        assertFalse(registry.succeeded(-2, message(902, 42)))
        assertFalse(automated.contains("42", "902"))

        val cleared = registry.begin(42)
        assertTrue(registry.pending(message(-3, 42, cleared.sendingId)))
        registry.clear()
        assertFalse(registry.pending(message(-4, 42, cleared.sendingId)))
        assertFalse(registry.succeeded(-3, message(903, 42)))

        val expired = registry.begin(42)
        clock.advance(Duration.ofDays(1))
        assertFalse(registry.pending(message(-5, 42, expired.sendingId)))
        registry.completed(expired, message(904, 42))
        assertFalse(automated.contains("42", "904"))
    }

    private fun connection(): TelegramConnectionState = TelegramConnectionState(CredentialStore(directory)).apply {
        status = TelegramStatus(state = "ready")
        version = 7
    }

    private fun message(id: Long, chat: Long, sendingId: Int? = null, text: String = "Synthetic reply"): TdApi.Message =
        TdApi.Message().apply {
            this.id = id
            chatId = chat
            isOutgoing = true
            sendingState = sendingId?.let { TdApi.MessageSendingStatePending(it) }
            content = TdApi.MessageText(TdApi.FormattedText(text, emptyArray()), null, null)
        }
}
