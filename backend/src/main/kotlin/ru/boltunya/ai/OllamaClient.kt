package ru.boltunya.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import ru.boltunya.ai.research.InternetResearcher
import ru.boltunya.ai.research.ResearchContext
import ru.boltunya.ai.research.ResearchLinkPolicy
import ru.boltunya.ai.research.ResearchPlanner
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.domain.ReplyResult
import ru.boltunya.exception.IntegrationException
import ru.boltunya.exception.impl.EmptyMemorySummaryException
import ru.boltunya.exception.impl.EmptyModelReplyException
import ru.boltunya.exception.impl.EngineHttpFailedException
import ru.boltunya.exception.impl.EngineUnavailableException
import ru.boltunya.exception.impl.ModelDownloadHttpFailedException
import ru.boltunya.exception.impl.ModelPullFailedException
import ru.boltunya.exception.impl.RegistryRedirectException
import ru.boltunya.exception.impl.RepetitiveModelReplyException
import ru.boltunya.memory.MemoryContext
import ru.boltunya.resources.ResourceCatalog
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private const val MEMORY_RESPONSE_TOKENS = 1536
private const val MEMORY_CONTEXT_TOKENS = 8192
private const val MEMORY_TEMPERATURE = 0.1
private const val MEMORY_PLANNER_CHARACTERS = 6000

private val resources = ResourceCatalog()

class OllamaClient(
    private val baseUrl: String = "http://127.0.0.1:11434",
    private val prompts: PromptBuilder = PromptBuilder(),
    private val quality: ReplyQualityGuard = ReplyQualityGuard(),
    private val researcher: InternetResearcher = InternetResearcher(),
    private val planner: ResearchPlanner = ResearchPlanner(),
    private val inference: ModelInference = ModelInference(),
    private val links: ResearchLinkPolicy = ResearchLinkPolicy(),
) {
    private val activeModels = ConcurrentHashMap.newKeySet<String>()
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()

    fun installed(): Set<String> {
        val response = request("/api/tags", null, Duration.ofSeconds(4))
        return response["models"]?.jsonArray?.stream()?.map {
            it.jsonObject["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        }?.toList()?.toSet() ?: emptySet()
    }

    fun reply(
        rule: ChatRule,
        history: List<ChatMessage>,
        globalPrompt: String = "",
        styleHistory: List<ChatMessage> = history,
        memory: MemoryContext = MemoryContext(),
    ): ReplyResult = inference.foreground { replyNow(rule, history, globalPrompt, styleHistory, memory) }

    private fun replyNow(
        rule: ChatRule,
        history: List<ChatMessage>,
        globalPrompt: String,
        styleHistory: List<ChatMessage>,
        memory: MemoryContext,
    ): ReplyResult {
        activeModels.add(rule.model)
        val started = System.nanoTime()
        val memoryFacts = if (memory.isEmpty()) {
            ""
        } else {
            resources.prompt(
                "ru/memory/context",
                mapOf("memory" to Json.encodeToString(memory.limited(MEMORY_PLANNER_CHARACTERS))),
            )
        }
        val instructions = globalPrompt + "\n" + rule.instructions + "\n" + memoryFacts
        fun generate(context: JsonArray, format: JsonObject? = null, maxTokens: Int = rule.maxTokens): JsonObject {
            val body = buildJsonObject {
                put("model", rule.model)
                put("messages", context)
                put("stream", false)
                if (format != null) put("format", format)
                if ((rule.model.startsWith("qwen3:") || rule.model.startsWith("qwen3.5:")) &&
                    !rule.model.endsWith("-instruct")
                ) {
                    put("think", false)
                }
                put("keep_alive", "20m")
                put(
                    "options",
                    buildJsonObject {
                        put("temperature", if (format != null) 0.0 else rule.creativity / 100.0)
                        put("num_predict", maxTokens)
                        put("num_ctx", 8192)
                        put("repeat_penalty", 1.05)
                        put("top_p", 0.8)
                        put("top_k", 20)
                    },
                )
            }
            return request("/api/chat", body, Duration.ofSeconds(90))
        }
        val research = if (rule.internetAccess && planner.needed(history)) {
            val plan = planner.plan(history, instructions) { context, format ->
                generate(
                    context,
                    format,
                    192,
                )["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()
            }
            val found = researcher.lookup(plan)
            if (found.status in setOf("weather", "search", "currency")) planner.remember(history, plan)
            found
        } else {
            ResearchContext()
        }
        if (research.status == "unavailable") {
            return ReplyResult(
                resources.prompt("ru/research/no-answer"),
                (System.nanoTime() - started) / 1_000_000,
                0.0,
            )
        }
        val messages = prompts.build(rule, history, globalPrompt, research, styleHistory, memory)
        var response = generate(messages)
        var text = response["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        if (quality.needsRetry(text, history, instructions, styleHistory)) {
            val retryBase = prompts.build(
                rule.copy(matchMyStyle = false),
                history,
                globalPrompt,
                research,
                emptyList(),
                memory,
            )
            val retry = JsonArray(
                listOf(
                    buildJsonObject {
                        put("role", "system")
                        put(
                            "content",
                            retryBase.first().jsonObject["content"]?.jsonPrimitive?.contentOrNull.orEmpty() +
                                "\n" + resources.prompt("ru/conversation/retry"),
                        )
                    },
                ) + retryBase.drop(1),
            )
            response = generate(retry)
            text = response["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        }
        if (text.isBlank()) {
            throw EmptyModelReplyException()
        }
        if (quality.needsRetry(text, history, instructions, styleHistory)) {
            throw RepetitiveModelReplyException()
        }
        text = links.format(text, rule, history, research)
        val count = response["eval_count"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val duration = response["eval_duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val speed = if (duration > 0) {
            count * 1e9 / duration
        } else {
            0.0
        }
        return ReplyResult(
            text,
            (System.nanoTime() - started) / 1_000_000,
            speed,
        )
    }

    fun warm(model: String) = inference.foreground { warmNow(model) }

    private fun warmNow(model: String) {
        activeModels.add(model)
        val body = buildJsonObject {
            put("model", model)
            put(
                "messages",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("role", "user")
                            put("content", resources.prompt("ru/common/warmup"))
                        },
                    ),
                ),
            )
            put("stream", false)
            if ((model.startsWith("qwen3:") || model.startsWith("qwen3.5:")) && !model.endsWith("-instruct")) {
                put("think", false)
            }
            put("keep_alive", "20m")
            put(
                "options",
                buildJsonObject {
                    put("num_predict", 1)
                    put("num_ctx", 8192)
                },
            )
        }
        request("/api/chat", body, Duration.ofMinutes(3))
    }

    fun summarize(model: String, instruction: String, data: String): String = inference.background {
        activeModels.add(model)
        val response = request(
            "/api/chat",
            buildJsonObject {
                put("model", model)
                put("stream", false)
                put("keep_alive", "20m")
                if (model.startsWith("qwen3:") || model.startsWith("qwen3.5:")) put("think", false)
                put(
                    "messages",
                    JsonArray(
                        listOf(
                            buildJsonObject {
                                put("role", "system")
                                put("content", instruction)
                            },
                            buildJsonObject {
                                put("role", "user")
                                put("content", data)
                            },
                        ),
                    ),
                )
                put(
                    "options",
                    buildJsonObject {
                        put("temperature", MEMORY_TEMPERATURE)
                        put("num_predict", MEMORY_RESPONSE_TOKENS)
                        put("num_ctx", MEMORY_CONTEXT_TOKENS)
                    },
                )
            },
            Duration.ofMinutes(3),
        )
        val text = response["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        if (text.isBlank()) throw EmptyMemorySummaryException()
        text.removePrefix("```markdown").removePrefix("```").removeSuffix("```").trim()
    }

    fun pull(model: String, onProgress: (Int) -> Unit) {
        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
        }
        val request = HttpRequest.newBuilder(URI.create("$baseUrl/api/pull"))
            .timeout(Duration.ofMinutes(45))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofLines())
        response.body().use { lines ->
            if (response.statusCode() != 200) {
                throw ModelDownloadHttpFailedException(response.statusCode())
            }
            lines.forEach { line ->
                val item = Json.parseToJsonElement(line).jsonObject
                if (item["error"] != null) {
                    val error = item["error"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (error.contains("redirect target not allowed")) {
                        throw RegistryRedirectException()
                    }
                    throw ModelPullFailedException()
                }
                val total = item["total"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val done = item["completed"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                if (total > 0) {
                    onProgress((done / total * 100).toInt())
                }
            }
        }
    }

    fun releaseModels() {
        activeModels.stream().forEach { model ->
            try {
                request(
                    "/api/generate",
                    buildJsonObject {
                        put("model", model)
                        put("keep_alive", 0)
                    },
                    Duration.ofSeconds(3),
                )
            } catch (exception: IntegrationException) {
                System.err.println("Model unload unavailable: " + exception.javaClass.simpleName)
            }
        }
        activeModels.clear()
    }

    private fun request(path: String, body: JsonObject?, timeout: Duration): JsonObject {
        val builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(timeout)
        if (body != null) {
            builder.header(
                "Content-Type",
                "application/json",
            ).POST(HttpRequest.BodyPublishers.ofString(body.toString()))
        }
        val response = try {
            http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        } catch (exception: java.io.IOException) {
            throw EngineUnavailableException(exception)
        }
        if (response.statusCode() != 200) {
            throw EngineHttpFailedException(response.statusCode())
        }
        return Json.parseToJsonElement(response.body()).jsonObject
    }
}
