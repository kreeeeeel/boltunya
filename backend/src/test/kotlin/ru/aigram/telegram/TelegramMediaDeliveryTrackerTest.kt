package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.junit.jupiter.api.Test
import ru.aigram.memory.AdvancingClock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException
import java.util.stream.IntStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelegramMediaDeliveryTrackerTest {
    @Test
    fun `waits for actual delivery and handles updates arriving before the album response`() {
        val tracker = TelegramMediaDeliveryTracker(Clock.systemUTC())
        tracker.succeeded("42", 100, 1, 101)
        tracker.succeeded("42", 100, 2, 102)
        assertEquals(listOf("101", "102"), tracker.await("42", listOf(pending(1), pending(2))))
    }

    @Test
    fun `does not confirm a different account or swallow send failures`() {
        val tracker = TelegramMediaDeliveryTracker(Clock.systemUTC())
        tracker.succeeded("43", 100, 1, 101)
        assertFailsWith<TimeoutException> { tracker.await("42", listOf(pending(1)), Duration.ZERO) }
        tracker.failed("42", 100, 2)
        assertFailsWith<ExecutionException> { tracker.await("42", listOf(pending(2))) }
    }

    @Test
    fun `retains an existing early receipt when the bounded cache reaches its limit`() {
        val clock = AdvancingClock(Instant.EPOCH)
        val tracker = TelegramMediaDeliveryTracker(clock)
        tracker.succeeded("42", 100, 1, 101)
        clock.advance(Duration.ofSeconds(1))
        IntStream.range(2, 1001).forEach { tracker.succeeded("42", 100, it.toLong(), it + 100L) }
        assertEquals(listOf("101"), tracker.await("42", listOf(pending(1)), Duration.ZERO))
    }

    @Test
    fun `terminal callback waits for a late album receipt after caller timeout`() {
        val tracker = TelegramMediaDeliveryTracker(Clock.systemUTC())
        val terminal = tracker.track("42", listOf(pending(1), pending(2)))
        var complete = false
        terminal.whenComplete { _, _ -> complete = true }
        assertFailsWith<TimeoutException> { terminal.get(0, java.util.concurrent.TimeUnit.MILLISECONDS) }
        assertFalse(complete)
        tracker.succeeded("42", 100, 1, 101)
        assertFalse(complete)
        tracker.succeeded("42", 100, 2, 102)
        assertEquals(listOf("101", "102"), terminal.join())
        assertTrue(complete)
    }

    private fun pending(messageId: Long) = TdApi.Message().apply {
        id = messageId
        chatId = 100
        sendingState = TdApi.MessageSendingStatePending()
    }
}
