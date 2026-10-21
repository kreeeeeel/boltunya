package ru.aigram.ai.research

import org.junit.jupiter.api.Test
import ru.aigram.domain.ChatMessage
import ru.aigram.domain.ChatRule
import ru.aigram.domain.SourceLinks
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResearchLinkPolicyTest {
    private val policy = ResearchLinkPolicy()
    private val source = ResearchSource("Kotlin", "https://kotlinlang.org/docs/releases.html", "Kotlin stable release")
    private val context = ResearchContext("search", "Fresh data", listOf(source))

    @Test
    fun `weather and normal answers do not acquire a link and generated invented URLs are removed`() {
        val history = history("Какая сейчас погода в Москве?")
        val weather = context.copy(status = "weather")
        assertEquals("В Москве дождь.", policy.format("В Москве дождь.", ChatRule(), history, weather))
        assertEquals(
            "В Москве дождь.",
            policy.format("В Москве дождь.\n\nhttps://made-up.example/weather", ChatRule(), history, weather),
        )
        assertEquals("", policy.forPrompt(ChatRule(), history, context).sources.single().url)
        assertFalse(
            policy.format(
                "Проверил. www.invented.example/news t.me/unverified",
                ChatRule(),
                history,
                context,
            ).contains("invented"),
        )
    }

    @Test
    fun `explicit link requests only receive a retrieved source and negations do not count as requests`() {
        val history = history("Какая версия Kotlin? Дай ссылку")
        val result = policy.format(
            "Актуальная версия Kotlin. https://invented.example/release",
            ChatRule(),
            history,
            context,
        )
        assertTrue(result.endsWith(source.url))
        assertFalse(result.contains("invented.example"))
        assertFalse(policy.visible(ChatRule(), history("Найди Kotlin, но без ссылок")))
        assertFalse(policy.visible(ChatRule(), history("Search Kotlin without links")))
        listOf("Дай ссылку", "Give a source", "Сілтеме бер", "Дай посилання").stream().forEach {
            assertTrue(policy.visible(ChatRule(), history(it)))
        }
    }

    @Test
    fun `owner modes override automatic inclusion and unrelated conversation links remain unchanged`() {
        val history = history("Последняя версия Kotlin?")
        assertTrue(
            policy.format(
                "Проверил.",
                ChatRule(sourceLinks = SourceLinks.ALWAYS),
                history,
                context,
            ).endsWith(source.url),
        )
        assertFalse(
            policy.format(
                source.url,
                ChatRule(sourceLinks = SourceLinks.NEVER),
                history("Дай ссылку"),
                context,
            ).contains("https://"),
        )
        assertEquals(
            "Наш сайт https://example.com",
            policy.format("Наш сайт https://example.com", ChatRule(), history, ResearchContext()),
        )
        assertEquals(
            source.url,
            policy.format("[Источник](${source.url})", ChatRule(), history("Дай источник"), context),
        )
    }

    private fun history(text: String): List<ChatMessage> = listOf(ChatMessage("1", "1", text, false, 0))
}
