package ru.boltunya.automation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.domain.ReplyResult
import ru.boltunya.exception.impl.ActivityDeliveryStartedException
import ru.boltunya.storage.SettingsStore
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReplyQueueTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `queue pause retains jobs and priority changes delivery order without bypassing delay`() {
        val gateway = FakeTelegramGateway()
        gateway.messages = gateway.messages + ChatMessage("2", "43", "Вторая реплика", false, 0)
        ReplyCoordinator(settings(), gateway) { _, history ->
            ReplyResult(history.last().text, 1, 1.0)
        }.use { replies ->
            replies.pauseQueue(true)
            gateway.messages.stream().forEach(replies::onMessage)
            assertEquals(2, replies.queue().queued)
            assertTrue(replies.activities().all { it.status == "queued" })
            val second = replies.activities().single { it.chatId == "43" }
            replies.prioritize(second.id)
            assertEquals(1, replies.activities().single { it.id == second.id }.queuePosition)
            assertTrue(replies.activities().single { it.id == second.id }.priority)
            Thread.sleep(1000)
            assertTrue(gateway.sent.isEmpty())
            replies.pauseQueue(false)
            await { gateway.sent.size == 2 }
            assertEquals(listOf("Вторая реплика", "Привет"), gateway.sent.toList())
            assertEquals(listOf("2", "1"), gateway.replyIds.toList())
        }
    }

    @Test
    fun `stopping active generation interrupts it and allows the next chat to proceed`() {
        val gateway = FakeTelegramGateway()
        gateway.messages = gateway.messages + ChatMessage("2", "43", "Следующая", false, 0)
        val started = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        ReplyCoordinator(settings(), gateway) { _, history ->
            if (history.last().chatId == "42") {
                started.countDown()
                try {
                    CountDownLatch(1).await(5, TimeUnit.SECONDS)
                } finally {
                    interrupted.countDown()
                }
            }
            ReplyResult(history.last().text, 1, 1.0)
        }.use { replies ->
            gateway.messages.stream().forEach(replies::onMessage)
            assertTrue(started.await(3, TimeUnit.SECONDS))
            val active = replies.activities().single { it.chatId == "42" }
            replies.cancel(active.id)
            assertTrue(interrupted.await(1, TimeUnit.SECONDS))
            await { gateway.sent.isNotEmpty() }
            assertEquals(listOf("Следующая"), gateway.sent.toList())
            assertEquals("cancelled", replies.details(active.id).entry.status)
            assertTrue(replies.details(active.id).response.isEmpty())
        }
    }

    @Test
    fun `clearing completed events preserves queued jobs and their details`() {
        val gateway = FakeTelegramGateway()
        ReplyCoordinator(settings(), gateway) { _, _ -> ReplyResult("Ответ", 1, 1.0) }.use { replies ->
            replies.pauseQueue(true)
            replies.onMessage(gateway.messages.first())
            val first = replies.activities().single()
            assertEquals("Привет", replies.details(first.id).trigger)
            replies.cancel(first.id)
            replies.onMessage(ChatMessage("2", "43", "Ещё сообщение", false, 0))
            replies.clearCompleted()
            assertEquals(1, replies.activities().size)
            assertEquals("queued", replies.activities().single().status)
            assertEquals(1, replies.queue().queued)
            assertFalse(replies.queue().running > 0)
        }
    }

    @Test
    fun `Telegram delivery does not block queue controls or outgoing update callbacks`() {
        val gateway = FakeTelegramGateway()
        val sending = CountDownLatch(1)
        val release = CountDownLatch(1)
        gateway.beforeSend = {
            sending.countDown()
            release.await(3, TimeUnit.SECONDS)
        }
        val controls = Executors.newSingleThreadExecutor()
        try {
            ReplyCoordinator(settings(), gateway) { _, _ -> ReplyResult("Ответ", 1, 1.0) }.use { replies ->
                replies.onMessage(gateway.messages.first())
                assertTrue(sending.await(3, TimeUnit.SECONDS))
                controls.submit {
                    val id = replies.activities().single().id
                    assertFailsWith<ActivityDeliveryStartedException> { replies.cancel(id) }
                    replies.pauseQueue(true)
                    replies.onMessage(ChatMessage("outgoing", "42", "Ответ", true, 0))
                }.get(1, TimeUnit.SECONDS)
                release.countDown()
                await { replies.activities().single().status == "sent" }
                assertEquals(listOf("Ответ"), gateway.sent.toList())
            }
        } finally {
            release.countDown()
            controls.shutdownNow()
        }
    }

    private fun settings(): SettingsStore {
        val store = SettingsStore(directory)
        listOf("42", "43").stream().forEach { store.saveRule(it, ChatRule(enabled = true)) }
        store.update { it.copy(paused = false, replyDelaySeconds = 0, replyIntervalSeconds = 0) }
        return store
    }

    private fun await(condition: () -> Boolean) {
        assertTrue(
            Stream.generate {
                Thread.sleep(20)
                condition()
            }.limit(200).anyMatch { it },
        )
    }
}
