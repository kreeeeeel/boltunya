package ru.boltunya.ai.profile

import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import ru.boltunya.exception.impl.ModelContextLimitException
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
        val messages = listOf(system, UserMessage("Old".repeat(200)), AssistantMessage("Old answer"), latest)
        assertEquals(listOf(system, messages[2], latest), ProfileContextBudget().select(target, messages, 32))
    }

    @Test
    fun `does not silently remove oversized latest message or system instructions`() {
        assertFailsWith<ModelContextLimitException> {
            ProfileContextBudget().select(target, listOf(SystemMessage("s".repeat(500)), UserMessage("latest")), 64)
        }
    }
}
