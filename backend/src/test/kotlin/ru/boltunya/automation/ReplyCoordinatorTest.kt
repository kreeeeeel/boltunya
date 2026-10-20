package ru.boltunya.automation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.diagnostics.DiagnosticLog
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.domain.ReplyResult
import ru.boltunya.exception.impl.InvalidModelReplyException
import ru.boltunya.exception.impl.TelegramRateLimitException
import ru.boltunya.resources.ResourceCatalog
import ru.boltunya.storage.AutomatedReplyStore
import ru.boltunya.storage.SettingsStore
import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReplyCoordinatorTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `invalid model output never reaches Telegram delivery`() {
        val gateway = FakeTelegramGateway()
        val diagnostics = DiagnosticLog(directory, Clock.systemUTC(), ResourceCatalog())
        ReplyCoordinator(enabledSettings(), gateway, diagnostics = diagnostics) { _, _ ->
            throw InvalidModelReplyException()
        }
            .use { coordinator ->
                coordinator.onMessage(gateway.messages[0])
                await { coordinator.activities().any { it.status == "error" } }
                assertTrue(gateway.sent.isEmpty())
                assertEquals(InvalidModelReplyException().message, coordinator.activities().single().detail)
                assertEquals("INVALID_MODEL_REPLY", diagnostics.report().errors.single().code)
                assertEquals("reply", diagnostics.report().errors.single().source)
            }
    }

    @Test
    fun `does not send an in-flight generated reply after its triggering message was deleted`() {
        val gateway = FakeTelegramGateway()
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        ReplyCoordinator(enabledSettings(), gateway) { _, _ ->
            started.countDown()
            finish.await(5, TimeUnit.SECONDS)
            ReplyResult("should not send", 0, 0.0)
        }.use { coordinator ->
            coordinator.onMessage(gateway.messages[0])
            assertTrue(started.await(3, TimeUnit.SECONDS))
            coordinator.onDeleted("42", setOf(gateway.messages[0].id))
            finish.countDown()
            await { coordinator.activities().any { it.status == "cancelled" } }
            assertTrue(gateway.sent.isEmpty())
        }
    }

    @Test
    fun `sends a reply for an enabled chat using its history`() {
        val settings = enabledSettings()
        val gateway = FakeTelegramGateway()
        ReplyCoordinator(settings, gateway) { _, history ->
            assertEquals("Привет", history.last().text)
            ReplyResult("Здравствуйте!", 20, 100.0)
        }.use { coordinator ->
            coordinator.onMessage(gateway.messages[0])
            await { gateway.sent.isNotEmpty() }
            assertEquals(listOf("Здравствуйте!"), gateway.sent.toList())
        }
    }

    @Test
    fun `cancels generation when owner replies manually`() {
        val gateway = FakeTelegramGateway()
        ReplyCoordinator(enabledSettings(), gateway) { _, _ -> ReplyResult("wrong", 0, 0.0) }.use { coordinator ->
            coordinator.onMessage(gateway.messages[0])
            coordinator.onMessage(ChatMessage("2", "42", "Manual", true, 0))
            assertEquals("cancelled", coordinator.activities().single().status)
            assertTrue(gateway.sent.isEmpty())
        }
    }

    @Test
    fun `does not send a generated reply after global pause`() {
        val settings = enabledSettings()
        val gateway = FakeTelegramGateway()
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        ReplyCoordinator(settings, gateway) { _, _ ->
            started.countDown()
            finish.await(5, TimeUnit.SECONDS)
            ReplyResult("cancelled", 0, 0.0)
        }.use { coordinator ->
            coordinator.onMessage(gateway.messages[0])
            assertTrue(started.await(5, TimeUnit.SECONDS))
            settings.update { it.copy(paused = true) }
            finish.countDown()
            await { coordinator.activities().any { it.status == "cancelled" } }
            assertTrue(gateway.sent.isEmpty())
        }
    }

    @Test
    fun `does not send an answer prepared with an outdated shared prompt`() {
        val settings = enabledSettings()
        val gateway = FakeTelegramGateway()
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        ReplyCoordinator(settings, gateway) { _, _ ->
            started.countDown()
            finish.await(5, TimeUnit.SECONDS)
            ReplyResult("outdated", 0, 0.0)
        }.use { coordinator ->
            coordinator.onMessage(gateway.messages[0])
            assertTrue(started.await(5, TimeUnit.SECONDS))
            settings.update { it.copy(globalPrompt = "New instructions") }
            finish.countDown()
            await { coordinator.activities().any { it.status == "cancelled" } }
            assertTrue(gateway.sent.isEmpty())
        }
    }

    private fun enabledSettings(): SettingsStore {
        val store = SettingsStore(directory)
        store.saveRule("42", ChatRule(enabled = true))
        store.update { it.copy(paused = false, replyDelaySeconds = 0, replyIntervalSeconds = 0) }
        return store
    }

    @Test
    fun `Telegram retry after postpones other chats without retrying the rejected send`() {
        val settings = enabledSettings()
        settings.saveRule("43", ChatRule(enabled = true))
        val gateway = FakeTelegramGateway()
        gateway.messages = gateway.messages + ChatMessage("2", "43", "Второй", false, 0)
        gateway.nextSendError = TelegramRateLimitException(1, IllegalStateException("Rate limited"))
        var rejectedAt = 0L
        ReplyCoordinator(settings, gateway) { _, history ->
            if (rejectedAt == 0L) rejectedAt = System.nanoTime()
            ReplyResult(history.last().text, 0, 0.0)
        }.use { coordinator ->
            gateway.messages.stream().forEach(coordinator::onMessage)
            await { gateway.sent.isNotEmpty() }
            assertEquals(listOf("Второй"), gateway.sent.toList())
            assertTrue(gateway.sendTimes.first() - rejectedAt >= TimeUnit.SECONDS.toNanos(1))
            assertEquals(1, coordinator.activities().count { it.status == "error" })
        }
    }

    @Test
    fun `queues different chats with a minimum send interval and shows typing only during generation`() {
        val settings = enabledSettings()
        settings.saveRule("43", ChatRule(enabled = true))
        settings.update { it.copy(replyIntervalSeconds = 1) }
        val gateway = FakeTelegramGateway()
        gateway.messages = gateway.messages + ChatMessage("2", "43", "Второй", false, 0)
        val generating = AtomicInteger()
        val automated = AutomatedReplyStore(directory)
        ReplyCoordinator(settings, gateway, automated) { _, history ->
            assertEquals(1, generating.incrementAndGet())
            Thread.sleep(80)
            generating.decrementAndGet()
            ReplyResult(history.last().text, 80, 0.0)
        }.use { coordinator ->
            gateway.messages.stream().forEach(coordinator::onMessage)
            await { gateway.sent.size == 2 && gateway.typing.lastOrNull() == false }
            assertEquals(listOf("Привет", "Второй"), gateway.sent.toList())
            assertTrue(gateway.sendTimes[1] - gateway.sendTimes[0] >= TimeUnit.SECONDS.toNanos(1))
            assertTrue(gateway.typing.contains(true))
            assertTrue(automated.contains("42", "sent-1"))
            assertTrue(automated.contains("43", "sent-2"))
        }
    }

    @Test
    fun `coalesces a message burst and respects the configurable delay and typing opt out`() {
        val settings = enabledSettings()
        settings.update { it.copy(replyDelaySeconds = 1, showTyping = false) }
        val gateway = FakeTelegramGateway()
        val started = System.nanoTime()
        ReplyCoordinator(settings, gateway) { _, history -> ReplyResult(history.last().text, 0, 0.0) }
            .use { coordinator ->
                coordinator.onMessage(gateway.messages[0])
                val last = ChatMessage("2", "42", "Последняя реплика", false, 0)
                gateway.messages = gateway.messages + last
                coordinator.onMessage(last)
                await { gateway.sent.isNotEmpty() }
                assertEquals(listOf(last.text), gateway.sent.toList())
                assertTrue(gateway.sendTimes.first() - started >= TimeUnit.SECONDS.toNanos(1))
                assertTrue(gateway.typing.isEmpty())
            }
    }

    private fun await(condition: () -> Boolean) {
        val ready = Stream.generate {
            Thread.sleep(20)
            condition()
        }.limit(250).anyMatch { it }
        assertTrue(ready, "Timed out waiting for asynchronous reply")
    }
}
