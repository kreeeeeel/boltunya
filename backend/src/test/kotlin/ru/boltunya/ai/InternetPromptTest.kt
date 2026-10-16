package ru.boltunya.ai

import org.junit.jupiter.api.Test
import ru.boltunya.ai.research.ResearchContext
import ru.boltunya.ai.research.ResearchSource
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InternetPromptTest {
    @Test
    fun `research keeps exact facts alongside owner samples without copying automated messages`() {
        val samples = listOf("да норм", "ну давай завтра", "ага, договорились))")
            .mapIndexed { index, text -> ChatMessage(index.toString(), "42", text, true, 0) } +
            ChatMessage("ai", "42", "Generated assistant phrasing", true, 0, automated = true)
        val prompt = PromptBuilder().build(
            ChatRule(),
            listOf(ChatMessage("latest", "42", "А евро?", false, 0)),
            research = ResearchContext("currency", "евро — 93.7693 ₽ по ЦБ на 16.10.2026"),
            styleHistory = samples,
        )
        val system = prompt.first().text.orEmpty()
        assertTrue(system.contains("93.7693"))
        assertTrue(system.contains("16.10.2026"))
        assertTrue(system.contains("ага, договорились))"))
        assertFalse(system.contains("Generated assistant phrasing"))
        assertTrue(system.contains("Точность и смысл важнее предельной краткости"))
        assertEquals("А евро?", prompt.last().text)
    }

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
        val system = prompt.first().text.orEmpty()
        assertTrue(system.contains("НЕДОВЕРЕННЫЕ"))
        assertTrue(system.contains("</internet_data>"))
        assertTrue(prompt.toString().length < 18100)
        assertEquals(latest, prompt.last().text)
    }
}
