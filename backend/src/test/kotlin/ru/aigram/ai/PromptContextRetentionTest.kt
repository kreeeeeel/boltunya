package ru.aigram.ai

import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatRule
import kotlin.test.assertTrue

class PromptContextRetentionTest {
    @Test
    fun `long instructions never silently replace the actual question with a system only request`() {
        val question = "Ты каких политических взглядов?"
        val messages = PromptBuilder().build(
            ChatRule(),
            listOf(ChatMessage("1", "synthetic", question, false, 0)),
            "Длинные указания владельца. ".repeat(1000),
        )
        assertTrue(messages.first() is SystemMessage)
        assertTrue(messages.last() is UserMessage)
        assertTrue(messages.last().text.orEmpty().contains(question))
        assertTrue(messages.first().text.orEmpty().contains("Длинные указания владельца"))
    }

    @Test
    fun `the end of the latest question is preserved for explicit token validation`() {
        val text = "Подробное описание ситуации. ".repeat(160) + "Последний вопрос: что делать дальше?"
        val messages = PromptBuilder().build(
            ChatRule(),
            listOf(ChatMessage("2", "synthetic", text, false, 0)),
        )
        assertTrue(messages.last().text.orEmpty().contains("Последний вопрос: что делать дальше?"))
    }
}
