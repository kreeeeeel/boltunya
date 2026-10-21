package ru.aigram.telegram

import org.junit.jupiter.api.Test
import ru.aigram.configuration.testBeanProvider
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TelegramMessageSenderTest {
    private val sender =
        TelegramMessageSender(testBeanProvider { throw UnsupportedOperationException("No Telegram in parser tests") })

    @Test
    fun `extracts structured Telegram rate limit durations without guessing other numbers`() {
        assertEquals(42L, sender.floodWait("FLOOD_WAIT_42"))
        assertEquals(60L, sender.floodWait("FLOOD_WAIT60"))
        assertEquals(123L, sender.floodWait("Too Many Requests: retry after 123 seconds"))
        assertNull(sender.floodWait("Some unrelated error 42"))
        assertNull(sender.floodWait("retry after -1"))
        assertNull(sender.floodWait("FLOOD_WAIT_999999999999999999999"))
    }
}
