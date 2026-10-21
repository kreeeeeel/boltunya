package ru.aigram.telegram

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.configuration.testBeanProvider
import ru.aigram.domain.speech.OutgoingVoiceNote
import ru.aigram.storage.AutomatedReplyStore
import ru.aigram.storage.CredentialStore
import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TelegramVoiceSenderTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `cancelled voice sending never resolves a connection or sends a native request`() {
        val connections = AtomicInteger()
        val connection = TelegramConnectionState(CredentialStore(directory))
        val sender = TelegramVoiceSender(
            testBeanProvider {
                connections.incrementAndGet()
                throw UnsupportedOperationException("No Telegram in voice cancellation test")
            },
            TelegramVoiceContent(),
            TelegramAutomatedSendRegistry(
                connection,
                TelegramCache(),
                AutomatedReplyStore(directory),
                Clock.systemUTC(),
            ),
            connection,
            TelegramOutgoingFileStore(directory, Clock.systemUTC()),
        )
        Thread.currentThread().interrupt()
        try {
            assertFailsWith<CancellationException> {
                sender.send(
                    "42",
                    OutgoingVoiceNote(directory.resolve("not-read.ogg"), 1, byteArrayOf()),
                    automated = true,
                )
            }
            assertTrue(Thread.currentThread().isInterrupted)
            assertEquals(0, connections.get())
        } finally {
            Thread.interrupted()
        }
    }
}
