package ru.boltunya.memory

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import ru.boltunya.ai.AiTestServices
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.resources.ResourceCatalog
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "TIHO_LIVE_AI", matches = "true")
class LiveMemoryTest {
    @Test
    fun `learns owner facts and style for another dialog without including assistant text`() {
        val client = AiTestServices()
        val model = System.getenv("TIHO_QUALITY_MODEL") ?: "gemma3:4b"
        assertTrue(model in client.installed())
        val summary = OllamaMemorySummarizer(client.completion, ResourceCatalog()).summarizeOwner(
            model,
            "Раньше жил в Москве.",
            listOf(
                MemorySource(
                    "1",
                    1792072800,
                    MemorySpeaker.OWNER,
                    "Я переехал в Казань, теперь живу там. Люблю чай и обычно пишу коротко, без официальных фраз.",
                    "text",
                    "",
                ),
                MemorySource(
                    "2",
                    1792072900,
                    MemorySpeaker.ASSISTANT,
                    "Я живу в Париже и работаю астронавтом",
                    "text",
                    "",
                ),
            ),
        )
        assertTrue(summary.contains("Казан", ignoreCase = true))
        assertTrue(!summary.contains("Париж", ignoreCase = true))
        val reply = client.reply(
            ChatRule(model = model, internetAccess = false, matchMyStyle = false, creativity = 20),
            listOf(ChatMessage("3", "43", "В каком городе ты теперь живёшь?", false, 1792073000)),
            memory = MemoryContext(ownerSummary = summary),
        )
        assertTrue(reply.text.contains("Казан", ignoreCase = true))
    }

    @Test
    fun `summarizes a corrected historical fact and uses it in a later reply`() {
        val client = AiTestServices()
        val model = System.getenv("TIHO_QUALITY_MODEL") ?: "gemma3:4b"
        assertTrue(
            model in client.installed(),
            "The quality model must already be installed; this test never downloads models.",
        )
        val summary = OllamaMemorySummarizer(client.completion, ResourceCatalog()).summarize(
            model,
            "## Владелец\nВ 2024 году жил в Москве [id 1]. Предпочитает короткие ответы.",
            listOf(
                MemorySource(
                    "100",
                    1792072800,
                    MemorySpeaker.OWNER,
                    "Я переехал в Казань. Теперь живу здесь, а из Москвы уже уехал.",
                    "text",
                    "",
                ),
                MemorySource("101", 1792072900, MemorySpeaker.CONTACT, "Понял, теперь ты в Казани", "text", "Друг"),
            ),
        )
        assertTrue(summary.contains("Казан", ignoreCase = true))
        val reply = client.reply(
            ChatRule(model = model, internetAccess = false, matchMyStyle = false, creativity = 20),
            listOf(ChatMessage("102", "42", "В каком городе ты теперь живёшь?", false, 1792073000)),
            memory = MemoryContext(summary = summary),
        )
        assertTrue(reply.text.contains("Казан", ignoreCase = true))
    }
}
