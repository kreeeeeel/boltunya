package ru.aigram.memory

import jakarta.inject.Provider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.ModelInference
import ru.aigram.domain.ChatMessage
import ru.aigram.domain.ChatRule
import ru.aigram.resources.ResourceCatalog
import ru.aigram.storage.AutomatedReplyStore
import ru.aigram.storage.SettingsStore
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemoryCoordinatorTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `cancels queued collection per chat and globally without losing notes or accepting stale summaries`() {
        val settings = SettingsStore(directory)
        settings.saveRule("42", ChatRule(enabled = true))
        settings.saveRule("43", ChatRule(enabled = true))
        val telegram = MemoryTelegramGateway()
        val store = MemoryStore(directory, ResourceCatalog())
        val clock = AdvancingClock(Instant.ofEpochSecond(100000))
        val inference = ModelInference()
        val busy = CountDownLatch(1)
        val release = CountDownLatch(1)
        val foreground = java.util.concurrent.CompletableFuture.runAsync {
            inference.foreground {
                busy.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
        }
        assertTrue(busy.await(2, TimeUnit.SECONDS))
        val automated = AutomatedReplyStore(directory)
        val processor = MemoryProcessor(
            telegram,
            store,
            MemoryBatchBuilder(automated),
            MemorySummarizer { _, _, _ -> "Сводка" },
            clock,
            OwnerMemorySummarizer { _, old, _ -> old },
        )
        MemoryModelServer().use { server ->
            MemoryCoordinator(
                store,
                settings,
                telegram,
                server.start().runtime,
                processor,
                inference,
                clock,
                automated,
                queueProvider = Provider { DirectMemoryWorkQueue() },
            ).use { coordinator ->
                try {
                    coordinator.refresh("42", immediate = true)
                    coordinator.refresh("43", immediate = true)
                    store.edit("42", "Ручная заметка", store.read("42").revision)
                    val previous = store.state("42") ?: error("state")
                    val revision = store.read("42").documentRevision
                    assertEquals(2, coordinator.document("").collection.queuedChats)
                    assertEquals(2, coordinator.document("43").collection.queuePosition)
                    coordinator.cancel("42")
                    assertEquals(false, coordinator.document("42").collection.active)
                    assertEquals(1, coordinator.document("43").collection.queuePosition)
                    assertEquals(false, store.commit(previous, revision, "Устаревшая сводка", previous))
                    assertEquals("Ручная заметка", store.read("42").notes)
                    coordinator.cancel("")
                    assertEquals(false, coordinator.document("").collection.active)
                } finally {
                    release.countDown()
                    foreground.get(2, TimeUnit.SECONDS)
                }
                coordinator.tick()
                assertTrue(telegram.reads.isEmpty())
            }
        }
    }

    @Test
    fun `global rebuild resets eligible summaries and owner profile while preserving all manual notes`() {
        val settings = SettingsStore(directory)
        settings.saveRule("42", ChatRule(enabled = true))
        settings.saveRule("43", ChatRule(enabled = true, useMemory = false))
        val telegram = MemoryTelegramGateway()
        telegram.messages = listOf(ChatMessage("1", "42", "Факт", false, 1))
        val store = MemoryStore(directory, ResourceCatalog())
        store.activate("100")
        store.observe("42", 1)
        store.edit("", "Ручной профиль", store.read("").revision)
        store.edit("42", "Ручная заметка беседы", store.read("42").revision)
        val state = store.state("42") ?: error("state")
        assertTrue(
            store.commit(
                state,
                store.read("42").documentRevision,
                "Старая сводка",
                state.copy(headId = 1, backfillComplete = true, dirty = false, ownerMemoryVersion = 1),
                OwnerMemoryUpdate(store.read(""), "Старый профиль"),
            ),
        )
        val clock = AdvancingClock(Instant.ofEpochSecond(100000))
        val automated = AutomatedReplyStore(directory)
        val processor =
            MemoryProcessor(
                telegram,
                store,
                MemoryBatchBuilder(automated),
                MemorySummarizer { _, _, _ ->
                    "Новая сводка"
                },
                clock,
                OwnerMemorySummarizer { _, old, _ -> old },
            )
        MemoryModelServer().use { server ->
            MemoryCoordinator(
                store,
                settings,
                telegram,
                server.start().runtime,
                processor,
                ModelInference(),
                clock,
                automated,
                queueProvider = Provider { DirectMemoryWorkQueue() },
            ).use { coordinator ->
                coordinator.refresh(rebuild = true)
                assertEquals("", store.read("").summary)
                assertEquals("Ручной профиль", store.read("").notes)
                assertEquals("", store.read("42").summary)
                assertEquals("Ручная заметка беседы", store.read("42").notes)
                assertEquals(0L, store.state("42")?.headId)
                assertEquals(null, store.state("43"))
                assertTrue(store.state("42")?.requested == true)
            }
        }
    }

    @Test
    fun `forced collection wakes the worker and prioritizes the requested chat without waiting for the interval`() {
        val settings = SettingsStore(directory)
        settings.saveRule("42", ChatRule(enabled = true))
        settings.saveRule("43", ChatRule(enabled = true))
        val telegram = MemoryTelegramGateway()
        telegram.messages =
            listOf(
                ChatMessage("1", "42", "Первый диалог", false, 1),
                ChatMessage("2", "43", "Новый контекст", false, 2),
            )
        val store = MemoryStore(directory, ResourceCatalog())
        store.activate("100")
        store.observe("43", 1)
        val previous = store.state("43") ?: error("state")
        val clock = AdvancingClock(Instant.ofEpochSecond(100000))
        assertTrue(
            store.commit(
                previous,
                store.read("43").documentRevision,
                "Ранее собранная память",
                previous.copy(
                    headId = 1,
                    observedId = 1,
                    dirty = false,
                    backfillComplete = true,
                    recentUpdatedAt = 100000,
                    ownerMemoryVersion = 1,
                ),
            ),
        )
        val summarized = CountDownLatch(1)
        val automated = AutomatedReplyStore(directory)
        val processor = MemoryProcessor(
            telegram,
            store,
            MemoryBatchBuilder(automated),
            MemorySummarizer { _, _, _ ->
                summarized.countDown()
                "Обновлено"
            },
            clock,
            OwnerMemorySummarizer { _, existing, _ -> existing },
        )
        MemoryModelServer().use { server ->
            MemoryCoordinator(
                store,
                settings,
                telegram,
                server.start().runtime,
                processor,
                ModelInference(),
                clock,
                automated,
                queueProvider = Provider { DirectMemoryWorkQueue() },
            ).use { coordinator ->
                coordinator.refresh("43", immediate = true)
                assertTrue(summarized.await(3, TimeUnit.SECONDS))
                assertEquals("43", telegram.reads.first().first)
                assertEquals(listOf("43"), telegram.reads.map { it.first })
            }
        }
    }

    @Test
    fun `automatically collects only AI enabled chats and never subscribes from owner messages`() {
        val settings = SettingsStore(directory)
        settings.saveRule("149", ChatRule(enabled = true))
        val telegram = MemoryTelegramGateway()
        telegram.chatIds = (1..150).map(Int::toString)
        telegram.messages = (1..150).map { ChatMessage("$it", "$it", "Я люблю чай", true, 1000L + it) }
        telegram.groupChats = setOf("150")
        val store = MemoryStore(directory, ResourceCatalog())
        val clock = AdvancingClock(Instant.ofEpochSecond(100000))
        val automated = AutomatedReplyStore(directory)
        val processor = MemoryProcessor(
            telegram,
            store,
            MemoryBatchBuilder(automated),
            MemorySummarizer { _, _, _ -> "Беседа" },
            clock,
            OwnerMemorySummarizer { _, _, _ -> "Любит чай" },
        )
        MemoryModelServer().use { server ->
            MemoryCoordinator(
                store,
                settings,
                telegram,
                server.start().runtime,
                processor,
                ModelInference(),
                clock,
                automated,
                queueProvider = Provider { DirectMemoryWorkQueue() },
            )
                .use { coordinator ->
                    coordinator.tick()
                    assertEquals(setOf("149"), store.states().map { it.chatId }.toSet())
                    assertEquals(1, telegram.reads.size)
                    val context = coordinator.context(
                        ChatRule(useMemory = false),
                        listOf(ChatMessage("1", "42", "Привет", false, 1)),
                    )
                    assertEquals("Любит чай", context.ownerSummary)
                    assertEquals("", context.summary)
                    assertEquals(null, store.state("42"))
                    val owner = ChatMessage("900", "10", "Я люблю кофе", true, 1000)
                    coordinator.observe(owner)
                    assertEquals(null, store.state("10"))
                    automated.record("11", "901")
                    coordinator.observe(ChatMessage("901", "11", "Автоматический ответ", true, 1000))
                    assertEquals(null, store.state("11"))
                }
        }
    }

    @Test
    fun `disabled groups are not automatically scanned but allow an explicit memory request`() {
        val settings = SettingsStore(directory)
        settings.saveRule("42", ChatRule(enabled = true))
        val telegram = MemoryTelegramGateway()
        telegram.groupChats = setOf("42")
        val message = ChatMessage("10", "42", "Сообщение группы", false, 10, group = true)
        telegram.messages = listOf(message)
        val store = MemoryStore(directory, ResourceCatalog())
        val clock = AdvancingClock(Instant.ofEpochSecond(100000))
        val processor =
            MemoryProcessor(
                telegram,
                store,
                MemoryBatchBuilder(AutomatedReplyStore(directory)),
                MemorySummarizer {
                        _,
                        _,
                        _,
                    ->
                    "Сводка"
                },
                clock,
                OwnerMemorySummarizer { _, existing, _ -> existing },
            )
        MemoryModelServer().use { server ->
            MemoryCoordinator(
                store,
                settings,
                telegram,
                server.start().runtime,
                processor,
                ModelInference(),
                clock,
                AutomatedReplyStore(directory),
                queueProvider = Provider { DirectMemoryWorkQueue() },
            ).use { coordinator ->
                coordinator.tick()
                coordinator.observe(message)
                coordinator.tick()
                assertTrue(telegram.reads.isEmpty())
                coordinator.refresh("42")
                coordinator.tick()
                assertEquals("42", telegram.reads.first().first)
                assertTrue(store.state("42")?.manual == true)
            }
        }
    }

    @Test
    fun `backfill does not postpone scheduled updates of fresh messages`() {
        val settings = SettingsStore(directory)
        settings.saveRule("42", ChatRule(enabled = true))
        val telegram = MemoryTelegramGateway()
        telegram.messages =
            (1..200).map { ChatMessage("$it", "42", "Длинное сообщение ".repeat(80), false, it.toLong()) }
        val store = MemoryStore(directory, ResourceCatalog())
        val clock = AdvancingClock(Instant.ofEpochSecond(100000))
        val processor =
            MemoryProcessor(
                telegram,
                store,
                MemoryBatchBuilder(AutomatedReplyStore(directory)),
                MemorySummarizer {
                        _,
                        _,
                        _,
                    ->
                    "Сводка"
                },
                clock,
                OwnerMemorySummarizer { _, existing, _ -> existing },
            )
        MemoryModelServer().use { server ->
            MemoryCoordinator(
                store,
                settings,
                telegram,
                server.start().runtime,
                processor,
                ModelInference(),
                clock,
                AutomatedReplyStore(directory),
                queueProvider = Provider { DirectMemoryWorkQueue() },
            ).use { coordinator ->
                coordinator.tick()
                coordinator.tick()
                clock.advance(Duration.ofHours(5))
                val next = ChatMessage("201", "42", "Новый факт", false, 201)
                telegram.messages = telegram.messages + next
                coordinator.observe(next)
                coordinator.tick()
                assertEquals(200L, store.state("42")?.headId)
                clock.advance(Duration.ofHours(1))
                coordinator.tick()
                assertEquals(201L, store.state("42")?.headId)
                assertTrue(store.state("42")?.backfillComplete == false)
            }
        }
    }

    @Test
    fun `startup processes selected chats only and skips unchanged completed chats after restart`() {
        val settings = SettingsStore(directory)
        settings.saveRule("42", ChatRule(enabled = true))
        val telegram = MemoryTelegramGateway()
        telegram.chatIds = (1..150).map(Int::toString)
        telegram.messages = listOf(ChatMessage("1", "42", "Важный факт", false, 1))
        val store = MemoryStore(directory, ResourceCatalog())
        val clock = AdvancingClock(Instant.ofEpochSecond(100000))
        var summaries = 0
        val processor =
            MemoryProcessor(
                telegram,
                store,
                MemoryBatchBuilder(AutomatedReplyStore(directory)),
                MemorySummarizer {
                        _,
                        _,
                        _,
                    ->
                    summaries++
                    "Подтверждённый факт"
                },
                clock,
                OwnerMemorySummarizer { _, existing, _ -> existing },
            )
        MemoryModelServer().use { server ->
            val ollama = server.start()
            MemoryCoordinator(
                store,
                settings,
                telegram,
                ollama.runtime,
                processor,
                ModelInference(),
                clock,
                AutomatedReplyStore(directory),
                queueProvider = Provider { DirectMemoryWorkQueue() },
            ).use { coordinator ->
                coordinator.tick()
                coordinator.tick()
                coordinator.tick()
                assertEquals(setOf("42"), telegram.reads.map { it.first }.toSet())
                assertEquals(1, summaries)
                assertTrue(store.state("42")?.backfillComplete == true)
            }
            val readCount = telegram.reads.size
            MemoryCoordinator(
                store,
                settings,
                telegram,
                ollama.runtime,
                processor,
                ModelInference(),
                clock,
                AutomatedReplyStore(directory),
                queueProvider = Provider { DirectMemoryWorkQueue() },
            ).use { it.tick() }
            assertEquals(readCount, telegram.reads.size)
        }
    }

    @Test
    fun `dirty history waits for the configured interval and never rereads the entire dialog`() {
        val settings = SettingsStore(directory)
        settings.saveRule("42", ChatRule(enabled = true))
        val telegram = MemoryTelegramGateway()
        telegram.messages = listOf(ChatMessage("1", "42", "Первая реплика", false, 1))
        val store = MemoryStore(directory, ResourceCatalog())
        val clock = AdvancingClock(Instant.ofEpochSecond(100000))
        val seen = mutableListOf<String>()
        val processor =
            MemoryProcessor(
                telegram,
                store,
                MemoryBatchBuilder(AutomatedReplyStore(directory)),
                MemorySummarizer {
                        _,
                        _,
                        sources,
                    ->
                    seen.addAll(sources.map { it.id })
                    "Сводка"
                },
                clock,
                OwnerMemorySummarizer { _, existing, _ -> existing },
            )
        MemoryModelServer().use { server ->
            MemoryCoordinator(
                store,
                settings,
                telegram,
                server.start().runtime,
                processor,
                ModelInference(),
                clock,
                AutomatedReplyStore(directory),
                queueProvider = Provider { DirectMemoryWorkQueue() },
            ).use { coordinator ->
                coordinator.tick()
                coordinator.tick()
                val reads = telegram.reads.size
                clock.advance(Duration.ofHours(5))
                val next = ChatMessage("2", "42", "Новый факт", false, 2)
                telegram.messages = telegram.messages + next
                coordinator.observe(next)
                coordinator.tick()
                assertEquals(reads, telegram.reads.size)
                clock.advance(Duration.ofHours(1))
                coordinator.tick()
                assertEquals(listOf("1", "2"), seen)
                assertEquals(2L, store.state("42")?.headId)
                val after = telegram.reads.size
                coordinator.tick()
                assertEquals(after, telegram.reads.size)
            }
        }
    }

    @Test
    fun `disabling memory skips background reads and removes memory from the response context`() {
        val settings = SettingsStore(directory)
        settings.saveRule("42", ChatRule(enabled = true))
        settings.update { it.copy(memoryEnabled = false) }
        val telegram = MemoryTelegramGateway()
        val store = MemoryStore(directory, ResourceCatalog())
        val clock = AdvancingClock(Instant.ofEpochSecond(100000))
        val processor =
            MemoryProcessor(
                telegram,
                store,
                MemoryBatchBuilder(AutomatedReplyStore(directory)),
                MemorySummarizer {
                        _,
                        _,
                        _,
                    ->
                    error("must not run")
                },
                clock,
                OwnerMemorySummarizer { _, existing, _ -> existing },
            )
        MemoryModelServer().use { server ->
            MemoryCoordinator(
                store,
                settings,
                telegram,
                server.start().runtime,
                processor,
                ModelInference(),
                clock,
                AutomatedReplyStore(directory),
                queueProvider = Provider { DirectMemoryWorkQueue() },
            ).use { coordinator ->
                coordinator.tick()
                assertTrue(telegram.reads.isEmpty())
                assertTrue(coordinator.context(ChatRule(), listOf(ChatMessage("1", "42", "Hello", false, 1))).isEmpty())
            }
        }
    }
}
