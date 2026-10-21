package ru.aigram.automation.proactive

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.diagnostics.DiagnosticLog
import ru.aigram.domain.ActivityKind
import ru.aigram.domain.ChatMessage
import ru.aigram.domain.ChatRule
import ru.aigram.domain.ChatSummary
import ru.aigram.domain.GroupReplyMode
import ru.aigram.domain.ProactiveMode
import ru.aigram.domain.ReplyResult
import ru.aigram.memory.AdvancingClock
import ru.aigram.resources.ResourceCatalog
import ru.aigram.storage.SettingsStore
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.IntStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProactiveCoordinatorTest {
    @TempDir lateinit var directory: Path
    private val clock = AdvancingClock(Instant.ofEpochSecond(100000))
    private val resources = ResourceCatalog()

    @Test
    fun `opt in waits a full idle interval uses real history and sends only once until a response`() {
        val settings = enabledSettings()
        val gateway = gateway()
        val histories = CopyOnWriteArrayList<List<ChatMessage>>()
        val ledger = ProactiveLedger(directory)
        ReplyCoordinator(settings, gateway) { _, _ -> error("regular generator must not be used") }.use { replies ->
            coordinator(settings, gateway, replies, ledger) { _, history ->
                histories.add(history)
                ReplyResult("How did it go?", 1, 1.0)
            }.use { coordinator ->
                coordinator.tick()
                clock.advance(Duration.ofDays(1))
                coordinator.tick()
                assertTrue(replies.activities().isEmpty(), "disabled by default")
                settings.update { it.copy(proactiveEnabled = true) }
                coordinator.tick()
                assertTrue(replies.activities().isEmpty(), "no catch-up on enabling")
                clock.advance(Duration.ofMinutes(4))
                coordinator.tick()
                assertTrue(replies.activities().isEmpty())
                clock.advance(Duration.ofMinutes(1))
                coordinator.tick()
                await { replies.activities().any { it.status == "sent" } }
                assertEquals(gateway.delegate.messages, histories.single())
                assertTrue(histories.single().last().outgoing)
                assertEquals(listOf("10"), gateway.delegate.replyIds.toList())
                assertEquals(ActivityKind.INITIATIVE, replies.activities().single().kind)
                clock.advance(Duration.ofDays(1))
                coordinator.tick()
                assertEquals(1, replies.activities().size)
            }
            coordinator(settings, gateway, replies, ProactiveLedger(directory)) { _, _ -> error("restart duplicate") }
                .use { restarted ->
                    restarted.tick()
                    clock.advance(Duration.ofMinutes(5))
                    restarted.tick()
                    assertEquals(1, gateway.delegate.sent.size)
                }
        }
    }

    @Test
    fun `per chat enable and interval override global values but explicit disable wins`() {
        val settings = enabledSettings()
        settings.saveRule(
            "42",
            ChatRule(enabled = true, proactiveMode = ProactiveMode.ENABLED, proactiveIntervalMinutes = 10),
        )
        val gateway = gateway()
        ReplyCoordinator(settings, gateway) { _, _ -> error("regular") }.use { replies ->
            coordinator(settings, gateway, replies) { _, _ -> ReplyResult("Hi", 1, 1.0) }.use { coordinator ->
                coordinator.tick()
                clock.advance(Duration.ofMinutes(5))
                coordinator.tick()
                assertTrue(replies.activities().isEmpty())
                clock.advance(Duration.ofMinutes(5))
                coordinator.tick()
                await { gateway.delegate.sent.size == 1 }
                settings.update { it.copy(proactiveEnabled = true) }
                settings.saveRule("42", settings.rule("42").copy(proactiveMode = ProactiveMode.DISABLED))
                coordinator.observe(ChatMessage("11", "42", "reply", false, 0))
                gateway.summaries = listOf(gateway.summaries.single().copy(lastMessageId = "12"))
                coordinator.tick()
                clock.advance(Duration.ofDays(1))
                coordinator.tick()
                assertEquals(1, gateway.delegate.sent.size)
            }
        }
    }

    @Test
    fun `new incoming message while generating cancels initiative before Telegram delivery`() {
        val settings = enabledSettings()
        settings.update { it.copy(proactiveEnabled = true) }
        val gateway = gateway()
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        ReplyCoordinator(settings, gateway) { _, _ -> error("regular") }.use { replies ->
            coordinator(settings, gateway, replies) { _, _ ->
                started.countDown()
                finish.await(3, TimeUnit.SECONDS)
                ReplyResult("outdated", 1, 1.0)
            }.use { coordinator ->
                try {
                    coordinator.tick()
                    clock.advance(Duration.ofMinutes(5))
                    coordinator.tick()
                    assertTrue(started.await(3, TimeUnit.SECONDS))
                    coordinator.observe(ChatMessage("11", "42", "new", false, 0))
                    gateway.summaries =
                        listOf(gateway.summaries.single().copy(lastMessageId = "11", lastMessageOutgoing = false))
                    finish.countDown()
                    await { replies.activities().any { it.status == "cancelled" } }
                    assertTrue(gateway.delegate.sent.isEmpty())
                } finally {
                    finish.countDown()
                }
            }
        }
    }

    @Test
    fun `broadcasts unread chats and inherited mention only groups do not receive initiatives`() {
        val settings = enabledSettings()
        settings.update { it.copy(proactiveEnabled = true, groupReplies = GroupReplyMode.MENTIONS) }
        val gateway = gateway()
        val base = gateway.summaries.single()
        gateway.summaries = listOf(
            base.copy(id = "42", kind = "channel"),
            base.copy(id = "43", unread = 1),
            base.copy(id = "44", kind = "supergroup"),
            base.copy(id = "100"),
        )
        listOf("43", "44", "100").stream().forEach { settings.saveRule(it, ChatRule(enabled = true)) }
        ReplyCoordinator(settings, gateway) { _, _ -> error("regular") }.use { replies ->
            coordinator(settings, gateway, replies) { _, _ -> ReplyResult("Hi", 1, 1.0) }.use { coordinator ->
                coordinator.tick()
                clock.advance(Duration.ofDays(1))
                coordinator.tick()
                assertTrue(replies.activities().isEmpty())
                settings.saveRule("44", ChatRule(enabled = true, proactiveMode = ProactiveMode.ENABLED))
                coordinator.tick()
                clock.advance(Duration.ofMinutes(5))
                coordinator.tick()
                await { gateway.delegate.sent.size == 1 }
                assertEquals("44", replies.activities().single().chatId)
            }
        }
    }

    @Test
    fun `paused queue resets idle baseline and only one initiative is admitted per pass`() {
        val settings = enabledSettings()
        settings.update { it.copy(proactiveEnabled = true, replyDelaySeconds = 120) }
        settings.saveRule("43", ChatRule(enabled = true))
        val gateway = gateway()
        gateway.summaries = gateway.summaries + gateway.summaries.single().copy(id = "43")
        ReplyCoordinator(settings, gateway) { _, _ -> error("regular") }.use { replies ->
            coordinator(settings, gateway, replies) { _, _ -> error("delayed") }.use { coordinator ->
                coordinator.tick()
                replies.pauseQueue(true)
                clock.advance(Duration.ofMinutes(5))
                coordinator.tick()
                assertTrue(replies.activities().isEmpty())
                replies.pauseQueue(false)
                coordinator.tick()
                clock.advance(Duration.ofMinutes(5))
                coordinator.tick()
                coordinator.tick()
                assertEquals(1, replies.queue().queued)
                assertTrue(replies.activities().single().waitUntil > 0)
                assertFalse(gateway.delegate.sent.isNotEmpty())
            }
        }
    }

    @Test
    fun `checkpoint disk failure is diagnosed without blocking normal message dispatch`() {
        val settings = enabledSettings()
        val gateway = gateway()
        val ledger = ProactiveLedger(directory)
        ledger.reserve("100", "42", "10", 100)
        Files.createDirectory(directory.resolve("automation/initiatives.json.tmp"))
        ReplyCoordinator(settings, gateway) { _, _ -> ReplyResult("regular reply", 1, 1.0) }.use { replies ->
            coordinator(settings, gateway, replies, ledger) { _, _ ->
                error("initiative must not run")
            }.use { coordinator ->
                val incoming = ChatMessage("11", "42", "Hi", false, 0)
                coordinator.observe(incoming)
                replies.onMessage(incoming)
                await { gateway.delegate.sent.size == 1 }
                assertEquals("regular reply", gateway.delegate.sent.single())
                val report = DiagnosticLog(directory, clock, resources).report()
                assertEquals(1, report.errors.size)
            }
        }
    }

    @Test
    fun `failed attempts wait another interval and a real response permits a later initiative`() {
        val settings = enabledSettings()
        settings.update { it.copy(proactiveEnabled = true) }
        val gateway = gateway()
        val attempts = AtomicInteger()
        ReplyCoordinator(settings, gateway) { _, _ -> error("regular") }.use { replies ->
            coordinator(settings, gateway, replies) { _, _ ->
                if (attempts.incrementAndGet() == 1) error("synthetic generation failure")
                ReplyResult("Hi again", 1, 1.0)
            }.use { coordinator ->
                coordinator.tick()
                clock.advance(Duration.ofMinutes(5))
                coordinator.tick()
                await { replies.activities().any { it.status == "error" } && !replies.hasInitiative() }
                coordinator.tick()
                clock.advance(Duration.ofMinutes(4))
                coordinator.tick()
                assertEquals(1, attempts.get())
                clock.advance(Duration.ofMinutes(1))
                coordinator.tick()
                await { gateway.delegate.sent.size == 1 && !replies.hasInitiative() }
                val incoming = ChatMessage("11", "42", "Real response", false, 2)
                coordinator.observe(incoming)
                gateway.delegate.messages =
                    gateway.delegate.messages + incoming + ChatMessage("12", "42", "Okay", true, 3)
                gateway.summaries = listOf(gateway.summaries.single().copy(lastMessageId = "12"))
                coordinator.tick()
                clock.advance(Duration.ofMinutes(5))
                coordinator.tick()
                await { gateway.delegate.sent.size == 2 }
                assertEquals(3, attempts.get())
            }
        }
    }

    @Test
    fun `uncertain Telegram delivery failure never retries an initiative without a new incoming response`() {
        val settings = enabledSettings()
        settings.update { it.copy(proactiveEnabled = true) }
        val gateway = gateway()
        gateway.delegate.nextSendError = IllegalStateException("synthetic lost delivery acknowledgement")
        ReplyCoordinator(settings, gateway) { _, _ -> error("regular") }.use { replies ->
            coordinator(settings, gateway, replies) { _, _ -> ReplyResult("Hello", 1, 1.0) }.use { coordinator ->
                coordinator.tick()
                clock.advance(Duration.ofMinutes(5))
                coordinator.tick()
                await { replies.activities().any { it.status == "error" } && !replies.hasInitiative() }
                assertTrue(ProactiveLedger(directory).get("100", "42")?.awaitingResponse == true)
                clock.advance(Duration.ofDays(1))
                coordinator.tick()
                assertEquals(1, replies.activities().size)
            }
        }
    }

    @Test
    fun `failed durable delivery reservation prevents any Telegram send`() {
        val settings = enabledSettings()
        settings.update { it.copy(proactiveEnabled = true) }
        val gateway = gateway()
        ReplyCoordinator(settings, gateway) { _, _ -> error("regular") }.use { replies ->
            coordinator(settings, gateway, replies) { _, _ ->
                Files.createDirectory(directory.resolve("automation/initiatives.json.tmp"))
                ReplyResult("Must not be sent", 1, 1.0)
            }.use { coordinator ->
                coordinator.tick()
                clock.advance(Duration.ofMinutes(5))
                coordinator.tick()
                await { replies.activities().any { it.status == "error" } }
                assertTrue(gateway.delegate.sent.isEmpty())
            }
        }
    }

    private fun enabledSettings(): SettingsStore = SettingsStore(directory).also { settings ->
        settings.update {
            it.copy(paused = false, proactiveIntervalMinutes = 5, replyDelaySeconds = 0, replyIntervalSeconds = 0)
        }
        settings.saveRule("42", ChatRule(enabled = true))
    }

    private fun gateway(): ProactiveTelegramGateway = ProactiveTelegramGateway().also {
        it.summaries =
            listOf(
                ChatSummary(
                    "42",
                    "Test",
                    "See you",
                    1,
                    0,
                    ChatRule(),
                    lastMessageId = "10",
                    lastMessageOutgoing = true,
                ),
            )
        it.delegate.messages =
            listOf(ChatMessage("9", "42", "Tomorrow?", false, 0), ChatMessage("10", "42", "See you", true, 1))
    }

    private fun coordinator(
        settings: SettingsStore,
        gateway: ProactiveTelegramGateway,
        replies: ReplyCoordinator,
        ledger: ProactiveLedger = ProactiveLedger(directory),
        generate: (ChatRule, List<ChatMessage>) -> ReplyResult,
    ): ProactiveCoordinator = ProactiveCoordinator(
        settings,
        gateway,
        replies,
        ledger,
        clock,
        resources,
        DiagnosticLog(directory, clock, resources),
        generate,
    )

    private fun await(condition: () -> Boolean) {
        assertTrue(
            IntStream.range(0, 500).anyMatch {
                Thread.sleep(10)
                condition()
            },
            "work did not finish",
        )
    }
}
