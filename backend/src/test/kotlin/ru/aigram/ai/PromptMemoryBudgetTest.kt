package ru.aigram.ai

import org.junit.jupiter.api.Test
import ru.aigram.ai.profile.ModelProvider
import ru.aigram.ai.profile.ProfileContextBudget
import ru.aigram.ai.profile.ResolvedModelTarget
import ru.aigram.ai.prompt.PromptTokenEstimator
import ru.aigram.domain.ChatMessage
import ru.aigram.domain.ChatRule
import ru.aigram.memory.MemoryContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromptMemoryBudgetTest {
    @Test
    fun `optional memory is fitted to explicit model budget while question and selected style survive`() {
        val builder = PromptBuilder()
        val rule = ChatRule(style = "business", maxTokens = 256)
        val question = "Последний важный вопрос"
        val history = listOf(ChatMessage("1", "test", question, false, 1))
        val target = ResolvedModelTarget(
            "api",
            ModelProvider.OPENAI_COMPATIBLE,
            "test",
            "http://localhost",
            false,
        )
        val mandatory = builder.build(rule, history, "Ручные указания")
        val size = mandatory.sumOf { PromptTokenEstimator().estimate(it, target.model) }
        val limited = target.copy(options = target.options.copy(contextWindow = size + 1024))
        val messages = builder.build(
            rule,
            history,
            "Ручные указания",
            memory = MemoryContext(summary = "Проверенный факт. ".repeat(1000)),
            inputTarget = limited,
            reservedOutputTokens = 256,
        )
        assertEquals(messages, ProfileContextBudget().select(limited, messages, 256, 256))
        assertTrue(messages.last().text.orEmpty().contains(question))
        assertTrue(messages.first().text.orEmpty().contains("Ручные указания"))
        assertTrue(messages.first().text.orEmpty().contains("Выбранный стиль диалога"))
        assertTrue(messages.first().text.orEmpty().length < 10000)
    }
}
