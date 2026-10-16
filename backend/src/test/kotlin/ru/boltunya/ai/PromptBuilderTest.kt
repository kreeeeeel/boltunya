package ru.boltunya.ai

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.memory.MemoryContext
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromptBuilderTest {
    private val builder = PromptBuilder()

    @Test
    fun `includes bounded long term memory while keeping recent messages and marking memory as data`() {
        val memory =
            MemoryContext(
                "Предпочитаю короткие ответы",
                "Собеседник — друг со школы",
                "Мы договорились встретиться в Казани. ".repeat(300),
            )
        val prompt = builder.build(
            ChatRule(),
            listOf(ChatMessage("1", "42", "Во сколько встречаемся?", false, 1)),
            memory = memory,
        )
        val system = prompt.first().jsonObject["content"]?.jsonPrimitive?.content.orEmpty()
        assertTrue(system.contains("друг со школы"))
        assertTrue(system.contains("вспомогательные данные"))
        assertTrue(system.contains("Казани"))
        assertEquals("Во сколько встречаемся?", prompt.last().jsonObject["content"]?.jsonPrimitive?.content)
        assertTrue(prompt.toString().length < 18100)
    }

    @Test
    fun `includes recent incoming and outgoing context with correct roles`() {
        val messages = (1..8).toList().stream().map {
            ChatMessage(it.toString(), "1", "message-$it", it % 2 == 0, 0)
        }.toList()
        val prompt = builder.build(ChatRule(contextMessages = 4, instructions = "Без встреч", emoji = false), messages)
        assertEquals(5, prompt.size)
        assertEquals("message-5", prompt[1].jsonObject["content"]?.jsonPrimitive?.content)
        assertEquals("user", prompt[1].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("assistant", prompt[2].jsonObject["role"]?.jsonPrimitive?.content)
        assertTrue(prompt[0].toString().contains("Без встреч"))
        assertTrue(prompt[0].toString().contains("Не используй эмодзи"))
    }

    @Test
    fun `includes shared owner instructions and per chat instructions without changing message roles`() {
        val prompt = builder.build(
            ChatRule(instructions = "Без эмодзи"),
            listOf(ChatMessage("1", "1", "Привет", false, 0)),
            "Не назначай встречи",
        )
        val system = prompt[0].jsonObject["content"]?.jsonPrimitive?.content.orEmpty()
        assertTrue(system.contains("Не назначай встречи"))
        assertTrue(system.contains("Без эмодзи"))
        assertEquals("user", prompt[1].jsonObject["role"]?.jsonPrimitive?.content)
    }

    @Test
    fun `includes media captions skips empty media and caps long message content`() {
        val prompt = builder.build(
            ChatRule(),
            listOf(
                ChatMessage("1", "1", "Подпись к фотографии", false, 0, "photo"),
                ChatMessage("3", "1", "", false, 0, "voice"),
                ChatMessage("2", "1", "x".repeat(50000), false, 0),
            ),
        )
        assertEquals(3, prompt.size)
        assertEquals("Подпись к фотографии", prompt[1].jsonObject["content"]?.jsonPrimitive?.content)
        assertEquals(1200, prompt[2].jsonObject["content"]?.jsonPrimitive?.content?.length)
        assertFalse(prompt.toString().contains("voice"))
    }

    @Test
    fun `provides local date and time as facts for current time questions`() {
        val clock = Clock.fixed(Instant.parse("2026-10-15T18:30:00Z"), ZoneId.of("Europe/Moscow"))
        val prompt = PromptBuilder(clock).build(ChatRule(), emptyList())
        assertTrue(prompt[0].toString().contains("15.10.2026"))
        assertTrue(prompt[0].toString().contains("21:30"))
    }

    @Test
    fun `reserves context space for long global and chat instructions and retains the latest message`() {
        val history = (1..30).toList().stream().map {
            ChatMessage(it.toString(), "1", "x".repeat(1200), it % 2 == 0, 0)
        }.toList() + ChatMessage("31", "1", "Последний вопрос", false, 0)
        val prompt = builder.build(
            ChatRule(contextMessages = 60, instructions = "a".repeat(4000)),
            history,
            "b".repeat(4000),
        )
        assertTrue(prompt.toString().length < 18100)
        assertEquals("Последний вопрос", prompt.last().jsonObject["content"]?.jsonPrimitive?.content)
    }
}
