package ru.aigram.ai

import org.junit.jupiter.api.Test
import ru.aigram.ai.profile.ProfileContextBudget
import ru.aigram.ai.prompt.PromptTokenEstimator
import ru.aigram.domain.ai.profile.ModelProvider
import ru.aigram.domain.ai.profile.ResolvedModelTarget
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.memory.MemoryContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConversationFormattingPromptTest {
    @Test
    fun `plain output is default and Markdown is explicit without changing roles or the selected style priority`() {
        val builder = PromptBuilder()
        val history = listOf(ChatMessage("1", "synthetic", "Как дела?", false, 1))
        val plain = builder.build(ChatRule(style = "business"), history)
        val markdown = builder.build(
            ChatRule(style = "business"),
            history,
            formatReplies = true,
        )
        assertTrue(plain.first().text.orEmpty().contains("Форматирование ответа выключено"))
        assertFalse(plain.first().text.orEmpty().contains("Форматирование ответа включено"))
        val system = markdown.first().text.orEmpty()
        assertTrue(system.contains("Форматирование ответа включено"))
        assertTrue(system.contains("**жирный**"))
        assertTrue(system.contains("||спойлер||"))
        assertTrue(system.contains("без обязательной разметки каждой фразы"))
        assertTrue(system.indexOf("Выбранный стиль диалога:") > system.indexOf("Форматирование ответа включено"))
        assertEquals("user", markdown.last().messageType.value)
        assertEquals(plain.last(), markdown.last())
    }

    @Test
    fun `output instructions and formatting remain mandatory when optional memory is budgeted`() {
        val builder = PromptBuilder()
        val rule = ChatRule(style = "business", maxTokens = 256)
        val history = listOf(ChatMessage("1", "synthetic", "Последний вопрос", false, 1))
        val target = ResolvedModelTarget(
            "api",
            ModelProvider.OPENAI_COMPATIBLE,
            "synthetic-model",
            "http://localhost",
            false,
        )
        val mandatory = builder.build(rule, history, formatReplies = true)
        val cost = mandatory.stream().mapToInt { PromptTokenEstimator().estimate(it, target.model) }.sum()
        val limited = target.copy(options = target.options.copy(contextWindow = cost + 1024))
        val messages = builder.build(
            rule,
            history,
            memory = MemoryContext(summary = "Синтетический факт. ".repeat(1000)),
            inputTarget = limited,
            reservedOutputTokens = 256,
            formatReplies = true,
        )

        assertEquals(messages, ProfileContextBudget().select(limited, messages, 256, 256))
        assertTrue(messages.first().text.orEmpty().contains("без JSON-конверта истории"))
        assertTrue(messages.first().text.orEmpty().contains("Форматирование ответа включено"))
        assertTrue(messages.last().text.orEmpty().contains("Последний вопрос"))
    }
}
