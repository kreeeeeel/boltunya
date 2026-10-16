package ru.boltunya.ai

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import ru.boltunya.ai.research.ResearchContext
import ru.boltunya.ai.research.ResearchSource
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InternetPromptTest {
    @Test
    fun `external excerpts are marked as untrusted and leave space for latest message with long owner instructions`() {
        val latest = "Последний вопрос " + "x".repeat(1180)
        val history = listOf(ChatMessage("1", "1", latest, false, 0))
        val research =
            ResearchContext(
                "search",
                "Получено сегодня",
                listOf(ResearchSource("Source", "https://example.com", "x".repeat(9000))),
            )
        val prompt = PromptBuilder().build(
            ChatRule(instructions = "a".repeat(4000)),
            history,
            "b".repeat(4000),
            research,
        )
        val system = prompt.first().jsonObject["content"]?.jsonPrimitive?.content.orEmpty()
        assertTrue(system.contains("НЕДОВЕРЕННЫЕ"))
        assertTrue(system.contains("</internet_data>"))
        assertTrue(prompt.toString().length < 18100)
        assertEquals(latest, prompt.last().jsonObject["content"]?.jsonPrimitive?.content)
    }
}
