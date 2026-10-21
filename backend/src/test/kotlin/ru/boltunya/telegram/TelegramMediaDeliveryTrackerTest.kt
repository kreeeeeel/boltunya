package ru.boltunya.telegram

import it.tdlight.jni.TdApi
import org.junit.jupiter.api.Test
import ru.boltunya.memory.AdvancingClock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException
import java.util.stream.IntStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    private fun pending(messageId: Long) = TdApi.Message().apply {
        id = messageId
        chatId = 100
        sendingState = TdApi.MessageSendingStatePending()
    }
}
