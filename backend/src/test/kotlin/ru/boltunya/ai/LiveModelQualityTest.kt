package ru.boltunya.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.function.ThrowingSupplier
import org.opentest4j.AssertionFailedError
import ru.boltunya.ai.research.ResearchPlanner
import ru.boltunya.automation.activity.ReplyTrace
import ru.boltunya.domain.ActivityEntry
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.memory.MemoryContext
import ru.boltunya.resources.ResourceCatalog
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "TIHO_LIVE_AI", matches = "true")
class LiveModelQualityTest {
    @Test
    fun `every installed model handles conversations context and verified facts without exposing internal analysis`() {
        LiveResearchFixtures().use { fixtures ->
            val services = AiTestServices(
                baseUrl = System.getenv("TIHO_TEST_OLLAMA_URL") ?: "http://127.0.0.1:11434",
                prompts = PromptBuilder(clock = fixtures.clock),
                researcher = fixtures.researcher(),
                planner = ResearchPlanner(clock = fixtures.clock),
            )
            val requested = System.getenv("TIHO_TEST_MODELS")?.split(',')
            val installed = services.installed().filter { requested == null || it in requested }
                .sortedWith(compareBy<String> { it != "qwen3:4b" }.thenBy { it })
            assertTrue(installed.isNotEmpty(), "No installed models to test")
            val requestedCases = System.getenv("TIHO_TEST_CASES")?.split(',')
            val cases = Json.parseToJsonElement(ResourceCatalog().text("testing/model-quality.json")).jsonArray
                .filter {
                    requestedCases == null || it.jsonObject.getValue("id").jsonPrimitive.content in requestedCases
                }
            val report = mutableListOf<JsonObject>()
            val style = listOf("да норм", "ага, договорились))", "ну давай завтра", "ща гляну", "ок, тогда вечером")
                .mapIndexed { index, text -> ChatMessage("style-$index", "style", text, true, 0) }
            val file = Path.of("build/reports/model-quality.json")
            Files.createDirectories(file.parent)
            installed.stream().forEach { model ->
                cases.stream().forEach { element ->
                    val case = element.jsonObject
                    val id = case.getValue("id").jsonPrimitive.content
                    val history = Json.decodeFromJsonElement<List<ChatMessage>>(case.getValue("history"))
                    val trace = ReplyTrace(
                        ActivityEntry(id, "synthetic", "Synthetic", "generating", model, 0),
                        history.last().text,
                    )
                    val outcome = buildJsonObject {
                        put("model", model)
                        put("case", id)
                        val started = System.nanoTime()
                        try {
                            val reply = assertTimeoutPreemptively(
                                Duration.ofMinutes(3),
                                ThrowingSupplier {
                                    services.tracing.capture(trace) {
                                        services.reply(
                                            ChatRule(
                                                model = model,
                                                style = case["style"]?.jsonPrimitive?.content ?: "friendly",
                                                maxTokens = 128,
                                                creativity = 20,
                                                emoji = false,
                                                internetAccess = case["research"]?.jsonPrimitive?.content == "true",
                                            ),
                                            history,
                                            styleHistory = style,
                                            memory = MemoryContext(
                                                ownerSummary = "Владелец работает программистом.",
                                                summary = "Собеседник — давний друг.",
                                            ),
                                        )
                                    }
                                },
                            )
                            val checks = case.getValue("expected").jsonArray.map { pattern ->
                                Regex(
                                    pattern.jsonPrimitive.content,
                                    RegexOption.IGNORE_CASE,
                                ).containsMatchIn(reply.text)
                            }
                            val valid = reply.text.isNotBlank() && checks.all { it } &&
                                !ReplyQualityGuard().containsInternalAnalysis(reply.text) &&
                                reply.text.length <= case.getValue("maxLength").jsonPrimitive.content.toInt() &&
                                !reply.text.contains("http")
                            put("passed", valid)
                            put("reply", reply.text)
                            put("durationMs", reply.durationMs)
                            put("tokensPerSecond", reply.tokensPerSecond)
                            put(
                                "checks",
                                buildJsonArray {
                                    checks.forEach { add(JsonPrimitive(it)) }
                                },
                            )
                            println("Model quality: $model / $id / ${if (valid) "PASS" else "FAIL"}")
                        } catch (exception: AssertionFailedError) {
                            put("passed", false)
                            put("error", "GenerationDeadlineExceeded")
                            put("durationMs", (System.nanoTime() - started) / 1_000_000)
                            println("Model quality: $model / $id / GenerationDeadlineExceeded")
                        } catch (exception: Exception) {
                            put("passed", false)
                            put("error", exception.javaClass.simpleName)
                            put("durationMs", (System.nanoTime() - started) / 1_000_000)
                            put(
                                "causes",
                                buildJsonArray {
                                    generateSequence<Throwable>(exception) { it.cause }.take(4).forEach {
                                        add(JsonPrimitive(it.javaClass.simpleName))
                                    }
                                },
                            )
                            println("Model quality: $model / $id / ${exception.javaClass.simpleName}")
                        }
                        val attempts = trace.details().attempts
                        put("attempts", attempts.size)
                        put("thinkingCharacters", attempts.sumOf { it.thinking.length })
                        put("inputTokens", attempts.sumOf { it.inputTokens })
                        put("outputTokens", attempts.sumOf { it.outputTokens })
                    }
                    report.add(outcome)
                    Files.writeString(file, Json.encodeToString(report))
                    if (System.getenv("TIHO_TRACE_REQUESTS") == "true") {
                        Files.writeString(
                            Path.of("build/reports/model-trace-latest.json"),
                            Json.encodeToString(trace.details()),
                        )
                    }
                }
                services.runtime.releaseModels()
            }
            val failures = report.filter { it.getValue("passed").jsonPrimitive.content != "true" }
            assertTrue(
                failures.isEmpty(),
                failures.joinToString("\n") {
                    "${it["model"]} / ${it["case"]}: ${it["error"] ?: it["reply"]}"
                },
            )
        }
    }
}
