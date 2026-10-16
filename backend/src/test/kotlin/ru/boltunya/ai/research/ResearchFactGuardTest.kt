package ru.boltunya.ai.research

import org.junit.jupiter.api.Test
import ru.boltunya.domain.ChatMessage
import ru.boltunya.resources.ResourceCatalog
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResearchFactGuardTest {
    private val guard = ResearchFactGuard(ResourceCatalog())

    @Test
    fun `source only requests may omit rates but cannot state a wrong rate`() {
        val context = ResearchContext("currency", requiredNumbers = listOf("93.7693"))
        fun history(text: String) = listOf(ChatMessage("1", "synthetic", text, false, 0))
        val sourceOnly = history("Дай ссылку на источник")
        val link = "Вот источник: https://www.cbr.ru/currency_base/daily/"
        assertTrue(guard.matches(link, context, sourceOnly))
        assertTrue(guard.matches("93,7693 ₽, $link", context, sourceOnly))
        assertFalse(guard.matches("93,77 ₽, $link", context, sourceOnly))
        assertFalse(guard.matches(link, context, history("Дай ссылку и курс евро")))
        assertFalse(guard.matches(link, context, history("Подробнее, но без ссылок")))
        assertFalse(guard.matches(link, context, history("А евро?")))
    }

    @Test
    fun `accepts equivalent decimal formatting but rejects rounding and numbers only in dates or links`() {
        val context = ResearchContext("currency", requiredNumbers = listOf("93.7693"))
        assertTrue(guard.matches("евро 93,7693 рубля на 16.10.2026", context))
        assertTrue(guard.matches("евро 93.769300 ₽", context))
        assertTrue(guard.matches("евро 93,7693руб.", context))
        assertFalse(guard.matches("евро 93,77 рубля на 16.10.2026", context))
        assertFalse(guard.matches("евро 83,76 рубля", context))
        assertFalse(guard.matches("курс здесь https://example.org/93.7693", context))
        assertFalse(guard.matches("курс на 16.10.2026", ResearchContext(requiredNumbers = listOf("16.1"))))
    }

    @Test
    fun `requires every supplied quote accepts thousands separators and leaves unconstrained facts alone`() {
        val context = ResearchContext(requiredNumbers = listOf("1234.56", "93.7693"))
        assertTrue(guard.matches("1 234,56 и 93,7693 рубля", context))
        assertTrue(guard.matches("1\u00a0234,56 и 93,7693 рубля", context))
        assertFalse(guard.matches("1 234,56 рубля", context))
        assertTrue(guard.matches("сегодня пасмурно", ResearchContext("weather")))
    }
}
