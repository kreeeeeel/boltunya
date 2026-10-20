package ru.boltunya.ai

import org.junit.jupiter.api.Test
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersonalStyleBuilderTest {
    @Test
    fun `preserves general voice without inserting unrelated sample topics into the prompt`() {
        val history = listOf("ща гляну))", "да норм", "ну погнали").mapIndexed { index, text ->
            ChatMessage(index.toString(), "synthetic", text, true, 0)
        }
        val style = PersonalStyleBuilder().build(history)
        assertTrue(style.contains("100%"))
        assertTrue(style.contains("совместимы с выбранным стилем диалога"))
        assertFalse(style.contains("Сохрани разговорность"))
        assertFalse(style.contains("owner_style"))
        assertFalse(style.contains("ща гляну"))
        assertFalse(style.contains("да норм"))
        assertFalse(style.contains("ну погнали"))
    }

    @Test
    fun `uses owner language and punctuation without copying incoming messages or bot boilerplate`() {
        val history = listOf(
            ChatMessage("1", "42", "Пиши официально", false, 0),
            ChatMessage("2", "42", "ща гляну))", true, 0),
            ChatMessage("3", "42", "да норм", true, 0),
            ChatMessage("4", "42", "ну погнали", true, 0),
            ChatMessage("5", "42", "Я ИИ-помощник, могу помочь с чем-то другим", true, 0),
            ChatMessage(
                "6",
                "42",
                "Машинный пример, который нельзя считать стилем владельца",
                true,
                0,
                automated = true,
            ),
        )
        val style = PersonalStyleBuilder().build(history)
        assertTrue(style.contains("100%"))
        assertFalse(style.contains("ща гляну))"))
        assertFalse(style.contains("Пиши официально"))
        assertFalse(style.contains("Я ИИ-помощник"))
        assertFalse(style.contains("Машинный пример"))
        assertFalse(style.contains("общем профиле владельца"))
        assertTrue(style.contains("Автоматические ответы ИИ не являются примерами"))
        assertTrue(style.contains("Факты и полный ответ по смыслу важнее краткости"))
    }

    @Test
    fun `style matching can be disabled and sparse histories do not invent a persona`() {
        val history = listOf("один", "два", "три").mapIndexed { index, text ->
            ChatMessage(index.toString(), "42", text, true, 0)
        }
        val builder = PromptBuilder()
        assertTrue(builder.build(ChatRule(), history).first().toString().contains("Обобщённая манера владельца"))
        assertFalse(
            builder.build(
                ChatRule(matchMyStyle = false),
                history,
            ).first().toString().contains("Обобщённая манера владельца"),
        )
        assertTrue(PersonalStyleBuilder().build(history.take(1)).contains("пока мало"))
        assertFalse(
            builder.build(ChatRule(matchMyStyle = false), history).first().toString().contains("Ладно, сегодня ты"),
        )
    }
}
