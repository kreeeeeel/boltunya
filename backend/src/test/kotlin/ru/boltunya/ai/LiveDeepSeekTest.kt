package ru.boltunya.ai

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "TIHO_LIVE_AI", matches = "true")
class LiveDeepSeekTest {
    @Test
    fun `installed catalog models return final answers through Spring AI with a short chat limit`() {
        val services = AiTestServices()
        val installed = services.installed().filter { ModelCatalog().contains(it) }
        assertTrue(installed.contains("deepseek-r1:8b"))
        installed.sorted().stream().forEach { model ->
            val reply = services.reply(
                ChatRule(model = model, maxTokens = 64, internetAccess = false, matchMyStyle = false),
                listOf(ChatMessage("1", "synthetic", "Привет! Как дела?", false, 0)),
            )
            assertTrue(reply.text.isNotBlank(), model)
            assertFalse(reply.text.contains("<think>", ignoreCase = true), model)
            assertTrue(reply.tokensPerSecond > 0, model)
            assertTrue(reply.durationMs > 0, model)
        }
    }
}
