package ru.aigram.ai.profile

import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import ru.aigram.exception.impl.ModelContextLimitException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProfileContextBudgetTest {
    private val target =
        ResolvedModelTarget(
            "api",
            ModelProvider.OPENAI_COMPATIBLE,
            "test",
            "https://example.test/v1",
            true,
            options = ModelProfileOptions(contextWindow = 512, maxOutputTokens = 64),
        )

    @Test
    fun `retains system and latest context while dropping old history to fit the text budget`() {
        val system = SystemMessage("Instructions")
        val latest = UserMessage("Latest")
        val messages = listOf(system, UserMessage("Old".repeat(1000)), AssistantMessage("Old answer"), latest)
        assertEquals(listOf(system, messages[2], latest), ProfileContextBudget().select(target, messages, 32))
    }

    @Test
    fun `does not silently remove oversized latest message or system instructions`() {
        assertFailsWith<ModelContextLimitException> {
            ProfileContextBudget().select(target, listOf(SystemMessage("s".repeat(3000)), UserMessage("latest")), 64)
        }
    }

    @Test
    fun `local Ollama requests enforce the same limit before inference`() {
        val local = target.copy(provider = ModelProvider.OLLAMA)
        assertFailsWith<ModelContextLimitException> {
            ProfileContextBudget().select(
                local,
                listOf(SystemMessage("Keep these instructions"), UserMessage("Long latest message ".repeat(1000))),
                64,
            )
        }
    }
}
