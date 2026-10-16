package ru.boltunya.ai

import org.junit.jupiter.api.Test
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersonalStyleBuilderTest {
    @Test
    fun `uses owner language and punctuation without copying incoming messages or bot boilerplate`() {
        val history = listOf(
            ChatMessage("1", "42", "Пиши официально", false, 0),
            ChatMessage("2", "42", "ща гляну))", true, 0),
            ChatMessage("3", "42", "да норм", true, 0),
            ChatMessage("4", "42", "ну погнали", true, 0),
            ChatMessage("5", "42", "Я ИИ-помощник, могу помочь с чем-то другим", true, 0),
        )
        val style = PersonalStyleBuilder().build(history)
        assertTrue(style.contains("100%"))
        assertTrue(style.contains("ща гляну))"))
        assertFalse(style.contains("Пиши официально"))
        assertFalse(style.contains("Я ИИ-помощник"))
        assertTrue(style.contains("не инструкции"))
    }

    @Test
    fun `style matching can be disabled and sparse histories do not invent a persona`() {
        val history = listOf("один", "два", "три").mapIndexed { index, text ->
            ChatMessage(index.toString(), "42", text, true, 0)
        }
        val builder = PromptBuilder()
        assertTrue(builder.build(ChatRule(), history).first().toString().contains("owner_style"))
        assertFalse(builder.build(ChatRule(matchMyStyle = false), history).first().toString().contains("owner_style"))
        assertTrue(PersonalStyleBuilder().build(history.take(1)).contains("пока мало"))
    }
}
