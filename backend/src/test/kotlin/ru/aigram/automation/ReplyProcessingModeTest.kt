package ru.aigram.automation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.chat.GroupReplyMode
import ru.aigram.domain.chat.ReplyInterruptionMode
import ru.aigram.domain.settings.ReplyProcessingMode
import ru.aigram.storage.AutomatedReplyStore
import ru.aigram.storage.SettingsStore
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.IntStream
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReplyProcessingModeTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `finish keeps the active private or group answer and coalesces only the next pending question`() {
        Stream.of(false, true).forEach { group ->
            val settings = settings(
                directory.resolve(group.toString()),
                if (group) {
                    ReplyProcessingMode.REPLACE
                } else {
                    ReplyProcessingMode.FINISH
                },
                if (group) {
                    ReplyInterruptionMode.FINISH
                } else {
                    ReplyInterruptionMode.INHERIT
                },
            )
            val gateway = gateway(group)
            val firstStarted = CountDownLatch(1)
            val releaseFirst = CountDownLatch(1)
            val nextStarted = CountDownLatch(1)
            val anchors = CopyOnWriteArrayList<String>()
            ReplyCoordinator(settings, gateway) { _, history ->
                val anchor = history.last().id
                anchors.add(anchor)
                if (anchors.size == 1) {
                    firstStarted.countDown()
                    releaseFirst.await(5, TimeUnit.SECONDS)
                } else {
                    nextStarted.countDown()
                }
                ReplyResult("answer-$anchor", 0, 0.0)
            }.use { coordinator ->
                try {
                    coordinator.onMessage(gateway.delegate.messages.single())
                    assertTrue(firstStarted.await(3, TimeUnit.SECONDS))
                    incoming(coordinator, gateway, message("2", group))
                    incoming(coordinator, gateway, message("3", group))
                    assertEquals(1, coordinator.queue().queued)
                    assertEquals(1, coordinator.queue().running)
                    assertFalse(nextStarted.await(1200, TimeUnit.MILLISECONDS), "same-chat inference must be serial")
                    releaseFirst.countDown()
                    await { gateway.delegate.sent.size == 2 && coordinator.queue().running == 0 }

                    assertEquals(listOf("1", "3"), anchors.toList())
                    assertEquals(listOf("answer-1", "answer-3"), gateway.delegate.sent.toList())
                    assertEquals(listOf("1", "3"), gateway.delegate.replyIds.toList())
                    assertEquals(1, coordinator.activities().count { it.status == "cancelled" })
                    assertEquals(2, coordinator.activities().count { it.status == "sent" })
                } finally {
                    releaseFirst.countDown()
                }
            }
        }
    }

    @Test
    fun `each preserves every pending anchor and excludes future messages even with four workers`() {
        val settings = settings(directory, ReplyProcessingMode.EACH)
        val gateway = gateway(true)
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val nextStarted = CountDownLatch(1)
        val contexts = CopyOnWriteArrayList<List<String>>()
        val calls = AtomicInteger()
        ReplyCoordinator(settings, gateway) { _, history ->
            contexts.add(history.stream().map { it.id }.toList())
            if (calls.incrementAndGet() == 1) {
                firstStarted.countDown()
                releaseFirst.await(5, TimeUnit.SECONDS)
            } else {
                nextStarted.countDown()
            }
            ReplyResult("answer-${history.last().id}", 0, 0.0)
        }.use { coordinator ->
            try {
                coordinator.onMessage(gateway.delegate.messages.single())
                assertTrue(firstStarted.await(3, TimeUnit.SECONDS))
                incoming(coordinator, gateway, message("2", true))
                incoming(coordinator, gateway, message("3", true))
                assertEquals(2, coordinator.queue().queued)
                assertFalse(nextStarted.await(1200, TimeUnit.MILLISECONDS), "pending jobs must not overlap this chat")
                releaseFirst.countDown()
                await { gateway.delegate.sent.size == 3 && coordinator.queue().running == 0 }

                assertEquals(listOf(listOf("1"), listOf("1", "2"), listOf("1", "2", "3")), contexts.toList())
                assertEquals(listOf("answer-1", "answer-2", "answer-3"), gateway.delegate.sent.toList())
                assertEquals(listOf("1", "2", "3"), gateway.delegate.replyIds.toList())
                assertEquals(listOf(9, 9, 9), gateway.delegate.topicIds.toList())
                assertEquals(listOf(2L, 3L, 4L), gateway.historyBefore.toList())
                assertTrue(coordinator.activities().stream().allMatch { it.status == "sent" })
            } finally {
                releaseFirst.countDown()
            }
        }
    }

    @Test
    fun `each restores a missing anchor without overflowing the native history cursor`() {
        Stream.of("3", Long.MAX_VALUE.toString()).forEach { id ->
            val settings = settings(directory.resolve(id), ReplyProcessingMode.EACH)
            val gateway = gateway(false)
            val anchor = message(id, false)
            gateway.delegate.messages = listOf(message("2", false), message("9", false))
            val contexts = CopyOnWriteArrayList<List<ChatMessage>>()
            ReplyCoordinator(settings, gateway) { _, history ->
                contexts.add(history)
                ReplyResult(history.last().text, 0, 0.0)
            }.use { coordinator ->
                coordinator.onMessage(anchor)
                await { gateway.delegate.sent.size == 1 }
                assertEquals(anchor, contexts.single().last())
                assertEquals(anchor.text, gateway.delegate.sent.single())
                assertEquals(id, gateway.delegate.replyIds.single())
                if (id == "3") {
                    assertEquals(listOf("2", "3"), contexts.single().stream().map { it.id }.toList())
                    assertEquals(4L, gateway.historyBefore.single())
                } else {
                    assertEquals(0L, gateway.historyBefore.single())
                }
            }
        }
    }

    @Test
    fun `explicit each overrides global replace and retains a burst before any job starts`() {
        val settings = settings(directory, ReplyProcessingMode.REPLACE, ReplyInterruptionMode.EACH)
        val gateway = gateway(false)
        gateway.delegate.messages = emptyList()
        ReplyCoordinator(settings, gateway) { _, history ->
            ReplyResult("answer-${history.last().id}", 0, 0.0)
        }.use { coordinator ->
            coordinator.pauseQueue(true)
            Stream.of("1", "2", "3").forEach { id -> incoming(coordinator, gateway, message(id, false)) }
            assertEquals(3, coordinator.queue().queued)
            assertEquals(0, coordinator.queue().running)
            coordinator.pauseQueue(false)
            await { gateway.delegate.sent.size == 3 && coordinator.queue().running == 0 }

            assertEquals(listOf("answer-1", "answer-2", "answer-3"), gateway.delegate.sent.toList())
            assertEquals(listOf("1", "2", "3"), gateway.delegate.replyIds.toList())
            assertTrue(coordinator.activities().stream().allMatch { it.status == "sent" })
        }
    }

    @Test
    fun `ineligible group activity retains pending anchors and typing in finish and each modes`() {
        Stream.of(ReplyProcessingMode.FINISH, ReplyProcessingMode.EACH).forEach { mode ->
            val settings = settings(directory.resolve(mode.name), mode)
            settings.update { it.copy(showTyping = true, ignoreAssistantTasks = true) }
            settings.saveRule("42", settings.rule("42").copy(groupReplies = GroupReplyMode.MENTIONS))
            val gateway = gateway(true)
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val anchors = CopyOnWriteArrayList<String>()
            ReplyCoordinator(settings, gateway) { _, history ->
                anchors.add(history.last().id)
                if (anchors.size == 1) {
                    started.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }
                ReplyResult("answer-${history.last().id}", 0, 0.0)
            }.use { coordinator ->
                try {
                    coordinator.onMessage(gateway.delegate.messages.single())
                    assertTrue(started.await(3, TimeUnit.SECONDS))
                    await { gateway.delegate.typing.contains(true) }
                    incoming(coordinator, gateway, message("2", true))
                    incoming(coordinator, gateway, message("3", true))
                    incoming(coordinator, gateway, message("4", true).copy(mentionsMe = false))
                    incoming(coordinator, gateway, message("5", true).copy(text = "напиши рецепт сырников"))
                    val expected = if (mode == ReplyProcessingMode.FINISH) {
                        listOf("1", "3")
                    } else {
                        listOf("1", "2", "3")
                    }
                    assertEquals(expected.size - 1, coordinator.queue().queued)
                    assertEquals(1, coordinator.queue().running)
                    assertFalse(gateway.typingCancelled.await(300, TimeUnit.MILLISECONDS))
                    release.countDown()
                    await { gateway.delegate.sent.size == expected.size && coordinator.queue().running == 0 }

                    assertEquals(expected, anchors.toList())
                    assertEquals(expected, gateway.delegate.replyIds.toList())
                    assertEquals(expected.size, coordinator.activities().count { it.status == "sent" })
                    assertEquals(
                        if (mode == ReplyProcessingMode.FINISH) {
                            1
                        } else {
                            0
                        },
                        coordinator.activities().count { it.status == "cancelled" },
                    )
                } finally {
                    release.countDown()
                }
            }
        }
    }

    @Test
    fun `exact automated outgoing updates cannot cancel each backlog or become manual owner activity`() {
        val settings = settings(directory, ReplyProcessingMode.EACH)
        val gateway = gateway(false)
        gateway.delegate.messages = emptyList()
        val automated = AutomatedReplyStore(directory)
        ReplyCoordinator(settings, gateway, automated = automated) { _, history ->
            ReplyResult("answer-${history.last().id}", 0, 0.0)
        }.use { coordinator ->
            gateway.delegate.beforeSend = {
                coordinator.onMessage(message("101", false).copy(outgoing = true, automated = true))
            }
            coordinator.pauseQueue(true)
            Stream.of("1", "2", "3").forEach { id -> incoming(coordinator, gateway, message(id, false)) }
            coordinator.pauseQueue(false)
            await { gateway.delegate.sent.size == 3 && coordinator.queue().running == 0 }

            assertEquals(listOf("answer-1", "answer-2", "answer-3"), gateway.delegate.sent.toList())
            assertEquals(listOf("1", "2", "3"), gateway.delegate.replyIds.toList())
            assertTrue(gateway.delegate.sentAutomated.stream().allMatch { it })
            assertTrue(coordinator.activities().stream().allMatch { it.status == "sent" })
        }
    }

    @Test
    fun `an unmatched manual outgoing update cancels active and pending work despite a historical ID collision`() {
        val settings = settings(directory, ReplyProcessingMode.EACH)
        val gateway = gateway(false)
        val automated = AutomatedReplyStore(directory)
        automated.record("42", "101")
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        ReplyCoordinator(settings, gateway, automated = automated) { _, _ ->
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            ReplyResult("must not send", 0, 0.0)
        }.use { coordinator ->
            try {
                coordinator.onMessage(gateway.delegate.messages.single())
                assertTrue(started.await(3, TimeUnit.SECONDS))
                incoming(coordinator, gateway, message("2", false))
                coordinator.onMessage(message("101", false).copy(outgoing = true))
                release.countDown()
                await { coordinator.queue().running == 0 }
                assertEquals(0, coordinator.queue().queued)
                assertTrue(gateway.delegate.sent.isEmpty())
                assertTrue(coordinator.activities().stream().allMatch { it.status == "cancelled" })
            } finally {
                release.countDown()
            }
        }
    }

    @Test
    fun `default replace and explicit replace override still cancel the old generation`() {
        assertEquals(ReplyProcessingMode.REPLACE, SettingsStore(directory).get().replyProcessingMode)
        Stream.of(false, true).forEach { replaceOverride ->
            val settings = settings(
                directory.resolve(replaceOverride.toString()),
                if (replaceOverride) {
                    ReplyProcessingMode.FINISH
                } else {
                    ReplyProcessingMode.REPLACE
                },
                if (replaceOverride) {
                    ReplyInterruptionMode.REPLACE
                } else {
                    ReplyInterruptionMode.INHERIT
                },
            )
            val gateway = gateway(false)
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val calls = AtomicInteger()
            ReplyCoordinator(settings, gateway) { _, history ->
                if (calls.incrementAndGet() == 1) {
                    started.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }
                ReplyResult("answer-${history.last().id}", 0, 0.0)
            }.use { coordinator ->
                try {
                    coordinator.onMessage(gateway.delegate.messages.single())
                    assertTrue(started.await(3, TimeUnit.SECONDS))
                    incoming(coordinator, gateway, message("2", false))
                    release.countDown()
                    await { gateway.delegate.sent.size == 1 && coordinator.queue().running == 0 }
                    assertEquals(listOf("answer-2"), gateway.delegate.sent.toList())
                    assertEquals(listOf("2"), gateway.delegate.replyIds.toList())
                    assertEquals(1, coordinator.activities().count { it.status == "cancelled" })
                } finally {
                    release.countDown()
                }
            }
        }
    }

    @Test
    fun `finish never bypasses manual owner pause account configuration explicit cancel or deletion guards`() {
        Stream.of("manual", "pause", "account", "rule", "mode", "cancel", "stop", "delete").forEach { reason ->
            val settings = settings(directory.resolve(reason), ReplyProcessingMode.FINISH)
            val gateway = gateway(false)
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            ReplyCoordinator(settings, gateway) { _, _ ->
                started.countDown()
                release.await(5, TimeUnit.SECONDS)
                ReplyResult("must not send", 0, 0.0)
            }.use { coordinator ->
                try {
                    coordinator.onMessage(gateway.delegate.messages.single())
                    assertTrue(started.await(3, TimeUnit.SECONDS))
                    val jobId = coordinator.activities().single().id
                    when (reason) {
                        "manual" -> coordinator.onMessage(message("2", false).copy(outgoing = true))
                        "pause" -> settings.update { it.copy(paused = true) }
                        "account" -> gateway.owner = "200"
                        "rule" -> settings.saveRule("42", settings.rule("42").copy(instructions = "New instructions"))
                        "mode" -> settings.update { it.copy(replyProcessingMode = ReplyProcessingMode.REPLACE) }
                        "cancel" -> coordinator.cancel(jobId)
                        "stop" -> coordinator.cancelReplies()
                        "delete" -> {
                            incoming(coordinator, gateway, message("2", false))
                            coordinator.onDeleted("42", setOf("1"))
                        }
                    }
                    release.countDown()
                    await { coordinator.queue().running == 0 }
                    if (reason == "account") {
                        assertTrue(coordinator.activities().isEmpty(), "events remain account scoped")
                        gateway.owner = "100"
                    }
                    assertEquals("cancelled", coordinator.activities().single { it.id == jobId }.status, reason)
                    assertTrue(gateway.delegate.sent.isEmpty(), reason)
                } finally {
                    release.countDown()
                }
            }
        }
    }

    private fun settings(
        path: Path,
        mode: ReplyProcessingMode,
        chatMode: ReplyInterruptionMode = ReplyInterruptionMode.INHERIT,
    ): SettingsStore = SettingsStore(path).also { settings ->
        settings.update {
            it.copy(
                paused = false,
                replyDelaySeconds = 0,
                replyIntervalSeconds = 0,
                showTyping = false,
                maxParallelTasks = 4,
                replyProcessingMode = mode,
            )
        }
        settings.saveRule(
            "42",
            ChatRule(enabled = true, groupReplies = GroupReplyMode.ALL, replyInterruptionMode = chatMode),
        )
    }

    private fun gateway(group: Boolean): ReplyProcessingGateway = ReplyProcessingGateway().also {
        it.delegate.messages = listOf(message("1", group))
    }

    private fun message(id: String, group: Boolean): ChatMessage = ChatMessage(
        id,
        "42",
        "question-$id",
        false,
        id.toLong(),
        group = group,
        mentionsMe = true,
        topicId = if (group) {
            9
        } else {
            0
        },
    )

    private fun incoming(coordinator: ReplyCoordinator, gateway: ReplyProcessingGateway, message: ChatMessage) {
        gateway.delegate.messages = gateway.delegate.messages + message
        coordinator.onMessage(message)
    }

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
