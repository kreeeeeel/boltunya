package ru.aigram.telegram

import org.junit.jupiter.api.Test
import ru.aigram.configuration.testBeanProvider
import ru.aigram.exception.impl.InvalidMediaIdentifierException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class TelegramMessageLinksTest {
    @Test
    fun `asks Telegram for the exact opaque message ID without synthesizing a public link`() {
        val links = TelegramMessageLinks(testBeanProvider { error("No Telegram in request tests") })
        val request = links.request(-100123, "987654321098765")
        assertEquals(-100123L, request.chatId)
        assertEquals(987654321098765L, request.messageId)
        assertEquals(0, request.mediaTimestamp)
        assertEquals(0, request.checklistTaskId)
        assertFalse(request.forAlbum)
        assertFalse(request.inMessageThread)
        listOf("0", "-1", "99999999999999999999", "https://example.org").stream().forEach {
            assertFailsWith<InvalidMediaIdentifierException> { links.request(42, it) }
        }
    }
}
