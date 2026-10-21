package ru.aigram.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.ai.chat.messages.UserMessage
import ru.aigram.ai.model.ModelPolicies
import ru.aigram.automation.activity.ReplyTrace
import ru.aigram.domain.ai.model.ThinkingMode
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.events.ActivityEntry
import ru.aigram.domain.events.ActivityPhase
import ru.aigram.resources.ResourceCatalog
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "TIHO_LIVE_AI", matches = "true")
class LiveActivityTraceTest {
    @TestFactory
    fun `installed models return final text and independent streamed traces`(): Stream<DynamicTest> {
        val ai = AiTestServices(System.getenv("TIHO_TEST_OLLAMA_URL") ?: "http://127.0.0.1:11434")
        val requested = System.getenv("TIHO_TEST_MODELS")?.split(',')
        val installed = ai.installed().filter { requested == null || it in requested }
        val policies = ModelPolicies(ResourceCatalog())
        val report = mutableListOf<JsonObject>()
        assertTrue(installed.isNotEmpty(), "No installed models available for live streaming checks")
        return installed.stream().sorted().map { model ->
            DynamicTest.dynamicTest(model) {
                val trace = ReplyTrace(ActivityEntry(model, "synthetic", "Synthetic", "generating", model, 0), "Привет")
                try {
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
                    report.add(
                        buildJsonObject {
                            put("model", model)
                            put("reply", result.text)
                            put("requestedThinking", policies.get(model).thinking.name)
                            put("thinkingCharacters", result.thinking.length)
                            put("inputTokens", attempt.inputTokens)
                            put("outputTokens", attempt.outputTokens)
                            put("durationMs", result.durationMs)
                            put("loadDurationMs", result.loadDurationMs)
                            put("promptDurationMs", result.promptDurationMs)
                            put("tokensPerSecond", result.tokensPerSecond)
                            put("finishReason", attempt.finishReason)
                        },
                    )
                    val path = Path.of("build/reports/streaming-quality.json")
                    Files.createDirectories(path.parent)
                    Files.writeString(path, Json.encodeToString(report))
                    assertTrue(attempt.complete)
                    assertTrue(attempt.outputTokens > 0)
                    assertEquals(result.rawText, attempt.response)
                    assertEquals(result.thinking, attempt.thinking)
                    assertEquals("stop", attempt.finishReason)
                    if (policies.get(model).thinking == ThinkingMode.ENABLED) {
                        assertTrue(attempt.thinking.isNotBlank(), "$model did not emit its thinking stream")
                    }
                    if (policies.get(model).thinking == ThinkingMode.DISABLED) {
                        assertTrue(attempt.thinking.isBlank(), "$model emitted thinking despite the disabled mode")
                    }
                } finally {
                    ai.runtime.releaseModels()
                }
            }
        }
    }
}
