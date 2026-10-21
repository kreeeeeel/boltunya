package ru.boltunya.ai.research

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResearchParsingTest {
    @Test
    fun `literal search vocabulary supports every bundled language and preserves word boundaries`() {
        val terms = ResearchTerms()
        listOf("Какая погода завтра?", "Weather tomorrow?", "Яка погода?", "Ауа райы қандай?")
            .stream().forEach { assertTrue(terms.contains("web-search", it), it) }
        assertFalse(terms.contains("currency-list", "или евро"))
        assertFalse(terms.contains("source-request", "hyperlinked"))
        assertTrue(terms.only("research-reuse", "Дай ссылку на источник!"))
        assertFalse(terms.only("research-reuse", "Дай ссылку и курс евро"))
        assertEquals(
            listOf("қазақша", "українська", "你好", "hello", "2026"),
            ResearchWords().tokens("Қазақша, українська! 你好 — Hello 2026"),
        )
    }

    @Test
    fun `public search privacy guard detects contact details without treating a date as a phone`() {
        val privacy = ResearchPrivacy()
        assertTrue(privacy.containsContactDetails("Найди owner@example.com"))
        assertTrue(privacy.containsContactDetails("Позвони +7 (999) 123-45-67"))
        assertFalse(privacy.containsContactDetails("курс на 21.10.2026"))
        assertFalse(privacy.containsContactDetails("цена 93,7693 рубля"))
    }

    @Test
    fun `decimal lexer accepts grouping spaces and excludes calendar dates`() {
        val values = ResearchNumbers().values("1 234,56 и 93.769300 на 21.10.2026")
        assertEquals(setOf("1234.56", "93.7693"), values.map { it.toPlainString() }.toSet())
        assertTrue(ResearchNumbers().values("21.10.2026").isEmpty())
    }

    @Test
    fun `CommonMark identifies balanced links and plain URLs without altering unrelated text`() {
        val markdown = ResearchMarkdown()
        assertEquals(listOf("https://example.com/a_(b)"), markdown.links("[the **source**](https://example.com/a_(b))"))
        assertEquals(
            "Обычный ответ **с выделением**.",
            markdown.mapLinks("Обычный ответ **с выделением**.") { _, _ ->
                ""
            },
        )
        assertFalse(
            markdown.mapLinks("Проверил. t.me/unverified www.example.com") { _, _ ->
                ""
            }.contains("unverified"),
        )
    }
}
