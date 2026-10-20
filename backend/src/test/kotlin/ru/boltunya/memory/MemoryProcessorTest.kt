package ru.boltunya.memory

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.domain.ChatMessage
import ru.boltunya.resources.ResourceCatalog
import ru.boltunya.storage.AutomatedReplyStore
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.stream.IntStream
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemoryProcessorTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `global profile receives only real owner sources and merges facts from different dialogs`() {
        val gateway = MemoryTelegramGateway()
        gateway.messages = listOf(
            ChatMessage("1", "42", "Я люблю чай", true, 1),
            ChatMessage("2", "42", "Я люблю насилие", true, 2),
            ChatMessage("3", "42", "Я живу в Париже", false, 3),
            ChatMessage("4", "42", "Я астронавт", true, 4, automated = true),
            ChatMessage("5", "43", "Я программист", true, 5),
        )
        val automated = AutomatedReplyStore(directory)
        automated.record("42", "2")
        val store = MemoryStore(directory, ResourceCatalog())
        store.activate("100")
        val seen = mutableListOf<MemorySource>()
        val processor = MemoryProcessor(
            gateway,
            store,
            MemoryBatchBuilder(automated),
            MemorySummarizer { _, _, _ -> "Контекст беседы" },
            Clock.fixed(Instant.ofEpochSecond(1000), ZoneOffset.UTC),
            OwnerMemorySummarizer { _, existing, sources ->
                seen.addAll(sources)
                existing + sources.joinToString("\n") { it.text }
            },
        )
        listOf("42", "43").stream().forEach { id ->
            store.observe(id)
            assertTrue(processor.process(store.state(id) ?: error("state"), "test", true))
        }
        assertEquals(listOf("1", "5"), seen.map { it.id })
        assertTrue(seen.all { it.speaker == MemorySpeaker.OWNER })
        assertTrue(store.read("").summary.contains("Я люблю чай"))
        assertTrue(store.read("").summary.contains("Я программист"))
    }

    @Test
    fun `failed owner summary leaves conversation checkpoint and both memories unchanged`() {
        val gateway = MemoryTelegramGateway()
        gateway.messages = listOf(ChatMessage("1", "42", "Я люблю чай", true, 1))
        val store = MemoryStore(directory, ResourceCatalog())
        store.activate("100")
        store.observe("42", 1)
        val state = store.state("42") ?: error("state")
        val processor = MemoryProcessor(
            gateway,
            store,
            MemoryBatchBuilder(AutomatedReplyStore(directory)),
            MemorySummarizer { _, _, _ -> "Chat" },
            Clock.systemUTC(),
            OwnerMemorySummarizer { _, _, _ -> throw IllegalStateException("model failure") },
        )
        kotlin.test.assertFailsWith<IllegalStateException> { processor.process(state, "test", true) }
        assertEquals(state, store.state("42"))
        assertEquals("", store.read("").summary)
        assertEquals("", store.read("42").summary)
    }

    @Test
    fun `walks deep history in bounded pages resumes and catches up more than one page of new messages`() {
        val gateway = MemoryTelegramGateway()
        gateway.messages =
            IntStream.rangeClosed(1, 450).mapToObj { id ->
                ChatMessage("$id", "42", "История ".repeat(40), id % 2 == 0, id.toLong())
            }.toList()
        val store = MemoryStore(directory, ResourceCatalog())
        store.activate("100")
        store.observe("42", 450)
        val seen = mutableListOf<String>()
        val summarizer =
            MemorySummarizer { _, _, sources ->
                seen.addAll(sources.map { it.id })
                "Сводка подтверждённых фактов"
            }
        val clock = Clock.fixed(Instant.ofEpochSecond(1000), ZoneOffset.UTC)
        fun processor(bank: MemoryStore) = MemoryProcessor(
            gateway,
            bank,
            MemoryBatchBuilder(AutomatedReplyStore(directory)),
            summarizer,
            clock,
            OwnerMemorySummarizer { _, existing, _ -> existing },
        )
        processor(store).process(store.state("42") ?: error("state"), "gemma3:4b", true)
        val resumed = MemoryStore(directory, ResourceCatalog())
        resumed.activate("100")
        Stream.generate { processor(resumed).process(resumed.state("42") ?: error("state"), "gemma3:4b", false) }
            .limit(200).takeWhile { resumed.state("42")?.backfillComplete != true }.count()
        assertTrue(resumed.state("42")?.backfillComplete == true)
        assertEquals((1..450).map(Int::toString).toSet(), seen.toSet())
        assertEquals(450, seen.size)
        gateway.messages =
            gateway.messages +
            IntStream.rangeClosed(451, 700).mapToObj { id ->
                ChatMessage("$id", "42", "Новая реплика ".repeat(35), false, id.toLong())
            }.toList()
        resumed.observe("42", 700, request = true)
        Stream.generate { processor(resumed).process(resumed.state("42") ?: error("state"), "gemma3:4b", true) }
            .limit(200).takeWhile {
                resumed.state("42")?.headId != 700L || resumed.state("42")?.deltaTargetId != 0L
            }.count()
        assertEquals((1..700).map(Int::toString).toSet(), seen.toSet())
        assertEquals(700, seen.size)
        assertEquals(700L, resumed.state("42")?.processedMessages)
        assertEquals(700, resumed.state("42")?.headId)
    }

    @Test
    fun `failed generation does not advance the checkpoint or discard previous memory`() {
        val gateway = MemoryTelegramGateway()
        gateway.messages = listOf(ChatMessage("10", "42", "Важный факт", false, 10))
        val store = MemoryStore(directory, ResourceCatalog())
        store.activate("100")
        store.observe("42", 10)
        val state = store.state("42") ?: error("state")
        val processor =
            MemoryProcessor(
                gateway,
                store,
                MemoryBatchBuilder(AutomatedReplyStore(directory)),
                MemorySummarizer {
                        _,
                        _,
                        _,
                    ->
                    throw IllegalStateException("model failure")
                },
                Clock.systemUTC(),
                OwnerMemorySummarizer { _, existing, _ -> existing },
            )
        kotlin.test.assertFailsWith<IllegalStateException> { processor.process(state, "gemma3:4b", true) }
        assertEquals(state, store.state("42"))
        assertEquals("", store.read("42").summary)
    }

    @Test
    fun `memory sources preserve the identity of each group participant`() {
        val batch = MemoryBatchBuilder(AutomatedReplyStore(directory)).select(
            listOf(
                ChatMessage("1", "42", "Я из Казани", false, 1, group = true, senderName = "Аня", senderId = "user:10"),
                ChatMessage(
                    "2",
                    "42",
                    "Я из Москвы",
                    false,
                    2,
                    group = true,
                    senderName = "Марк",
                    senderId = "user:20",
                ),
            ),
        )
        assertEquals(listOf("Аня", "Марк"), batch.sources.map { it.sender })
        assertEquals(listOf("user:10", "user:20"), batch.sources.map { it.senderId })
    }

    @Test
    fun `distinguishes real owner messages from known automated replies in summary sources`() {
        val automated = AutomatedReplyStore(directory)
        automated.record("42", "2")
        val batch = MemoryBatchBuilder(automated).select(
            listOf(
                ChatMessage("1", "42", "Я люблю чай", true, 1),
                ChatMessage("2", "42", "Обещаю приехать завтра", true, 2),
                ChatMessage("3", "42", "Привет", false, 3),
            ),
        )
        assertEquals(
            listOf(MemorySpeaker.OWNER, MemorySpeaker.ASSISTANT, MemorySpeaker.CONTACT),
            batch.sources.map {
                it.speaker
            },
        )
    }
}
