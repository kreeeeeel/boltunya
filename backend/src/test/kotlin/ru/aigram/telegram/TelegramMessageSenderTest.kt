package ru.aigram.telegram

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.configuration.testBeanProvider
import ru.aigram.domain.chat.MessageEntity
import ru.aigram.exception.impl.InvalidMessageEntitiesException
import ru.aigram.storage.AutomatedReplyStore
import ru.aigram.storage.CredentialStore
import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TelegramMessageSenderTest {
    @TempDir lateinit var directory: Path

    private val sender: TelegramMessageSender
        get() = TelegramMessageSender(
            testBeanProvider {
                throw UnsupportedOperationException("No Telegram in parser tests")
            },
            TelegramOutgoingEntities(),
            automatedSends(),
        )

    @Test
    fun `extracts structured Telegram rate limit durations without guessing other numbers`() {
        assertEquals(42L, sender.floodWait("FLOOD_WAIT_42"))
        assertEquals(60L, sender.floodWait("FLOOD_WAIT60"))
        assertEquals(123L, sender.floodWait("Too Many Requests: retry after 123 seconds"))
        assertNull(sender.floodWait("Some unrelated error 42"))
        assertNull(sender.floodWait("retry after -1"))
        assertNull(sender.floodWait("FLOOD_WAIT_999999999999999999999"))
    }

    @Test
    fun `invalid outgoing formatting is rejected before resolving a Telegram connection`() {
        val connections = AtomicInteger()
        val sender = TelegramMessageSender(
            testBeanProvider {
                connections.incrementAndGet()
                throw UnsupportedOperationException("No Telegram connection")
            },
            TelegramOutgoingEntities(),
            automatedSends(),
        )
        assertFailsWith<InvalidMessageEntitiesException> {
            sender.send("42", "😀", "", 0, listOf(MessageEntity(1, 1, "bold")))
        }
        assertEquals(0, connections.get())
    }

    private fun automatedSends(): TelegramAutomatedSendRegistry = TelegramAutomatedSendRegistry(
        TelegramConnectionState(CredentialStore(directory)),
        TelegramCache(),
        AutomatedReplyStore(directory),
        Clock.systemUTC(),
    )
}
