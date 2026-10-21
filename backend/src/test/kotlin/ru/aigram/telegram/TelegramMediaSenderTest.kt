package ru.aigram.telegram

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.configuration.testBeanProvider
import ru.aigram.domain.media.OutgoingMedia
import ru.aigram.domain.media.OutgoingMediaKind
import ru.aigram.exception.impl.MediaUnavailableException
import ru.aigram.storage.CredentialStore
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TelegramMediaSenderTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `partial album preparation releases undispatched copies without exposing original staged files`() {
        val content = TelegramMediaContent(TelegramOutgoingEntities())
        val outgoing = TelegramOutgoingFileStore(directory, Clock.systemUTC())
        val source = Files.writeString(directory.resolve("photo.jpg"), "Synthetic photo")
        val pending = outgoing.retain(source)
        val sender = TelegramMediaSender(
            testBeanProvider { error("No native client must be resolved during preparation") },
            content,
            TelegramMediaDeliveryTracker(Clock.systemUTC()),
            outgoing,
            TelegramConnectionState(CredentialStore(directory)),
        )
        val request = content.request(
            42,
            listOf(
                OutgoingMedia(source, OutgoingMediaKind.PHOTO),
                OutgoingMedia(directory.resolve("missing.jpg"), OutgoingMediaKind.PHOTO),
            ),
            "",
            0,
        )
        assertFailsWith<MediaUnavailableException> { sender.retainInputs(request) }
        outgoing.clearCompleted()
        assertEquals("Synthetic photo", Files.readString(source))
        assertTrue(Files.exists(pending))
        assertEquals(1L, outgoing.statistics().fileCount)
    }
}
