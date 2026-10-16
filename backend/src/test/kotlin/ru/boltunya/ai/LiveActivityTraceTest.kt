package ru.boltunya.ai

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.ai.chat.messages.UserMessage
import ru.boltunya.automation.activity.ActivityPhase
import ru.boltunya.automation.activity.ReplyTrace
import ru.boltunya.domain.ActivityEntry
import ru.boltunya.domain.ChatRule
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "TIHO_LIVE_AI", matches = "true")
class LiveActivityTraceTest {
    @TestFactory
    fun `installed models return final text and independent streamed traces`(): Stream<DynamicTest> {
        val ai = AiTestServices(System.getenv("TIHO_TEST_OLLAMA_URL") ?: "http://127.0.0.1:11434")
        val installed = ai.installed()
        assertTrue(installed.isNotEmpty(), "No installed models available for live streaming checks")
        return installed.stream().sorted().map { model ->
            DynamicTest.dynamicTest(model) {
                val trace = ReplyTrace(ActivityEntry(model, "synthetic", "Synthetic", "generating", model, 0), "Привет")
                trace.phase(ActivityPhase.REPLY)
                val result = ai.tracing.capture(trace) {
                    ai.completion.generate(
                        ChatRule(model = model, maxTokens = 128, creativity = 10),
                        listOf(UserMessage("Ответь одним коротким приветствием на русском языке без пояснений.")),
                    )
                }
                assertTrue(result.text.isNotBlank(), "$model returned no final text")
                assertTrue(!result.text.contains("<think>"), "$model leaked a thinking tag")
                val attempt = trace.details().attempts.last()
                assertTrue(attempt.complete)
                assertTrue(attempt.outputTokens > 0)
                assertEquals(result.rawText, attempt.response)
                assertEquals(result.thinking, attempt.thinking)
                assertEquals("stop", attempt.finishReason)
                if (model.startsWith("deepseek-r1") || model == "qwen3:4b") {
                    assertTrue(attempt.thinking.isNotBlank(), "$model did not emit its thinking stream")
                }
            }
        }
    }
}
