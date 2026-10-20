package ru.boltunya.ai.model

import jakarta.inject.Inject
import kotlinx.serialization.json.JsonObject
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.metadata.ChatGenerationMetadata
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.ollama.api.OllamaChatOptions
import org.springframework.ai.ollama.api.ThinkOption
import org.springframework.ai.retry.NonTransientAiException
import org.springframework.ai.retry.TransientAiException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.reactive.function.client.WebClientException
import org.springframework.web.reactive.function.client.WebClientResponseException
import ru.boltunya.ai.ModelInference
import ru.boltunya.ai.OllamaRuntime
import ru.boltunya.automation.activity.AttemptTrace
import ru.boltunya.automation.activity.ReplyTraceScope
import ru.boltunya.domain.ChatRule
import ru.boltunya.domain.ModelMeasurement
import ru.boltunya.exception.impl.EmptyMemorySummaryException
import ru.boltunya.exception.impl.EmptyModelReplyException
import ru.boltunya.exception.impl.EngineHttpFailedException
import ru.boltunya.exception.impl.EngineUnavailableException
import ru.boltunya.exception.impl.InvalidModelReplyException
import ru.boltunya.storage.ModelMeasurementStore
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

private const val CONTEXT_TOKENS = 8192
private const val MEMORY_RESPONSE_TOKENS = 1536
private const val MAX_REASONING_TOKENS = 4096
private const val RETRY_RESPONSE_TOKENS = 512
private const val KEEP_ALIVE = "20m"
private const val MEMORY_CREATIVITY = 10

class ChatCompletionService @Inject constructor(
    private val model: ChatModel,
    private val policies: ModelPolicies,
    private val reader: ModelReplyReader,
    private val runtime: OllamaRuntime,
    private val inference: ModelInference,
    private val measurements: ModelMeasurementStore,
    private val tracing: ReplyTraceScope = ReplyTraceScope(),
) {
    private val mapper = JsonMapper.builder().build()

    fun generate(
        rule: ChatRule,
        messages: List<Message>,
        schema: JsonObject? = null,
        maxTokens: Int = rule.maxTokens,
    ): ModelCompletion {
        val policy = policies.get(rule.model)
        val first = call(rule, messages, schema, maxTokens, policy, policy.reasoningTokens)
        val result = if (first.finishReason == "length") {
            call(
                rule,
                messages,
                schema,
                maxOf(maxTokens, RETRY_RESPONSE_TOKENS),
                policy,
                if (policy.reasoningTokens > 0) MAX_REASONING_TOKENS else 0,
            )
        } else {
            first
        }
        if (result.finishReason == "length" && result.text.isNotBlank()) throw InvalidModelReplyException()
        if (result.text.isNotBlank() && result.tokensPerSecond > 0) {
            measurements.record(rule.model, ModelMeasurement(result.durationMs, result.tokensPerSecond))
        }
        return result
    }

    private fun call(
        rule: ChatRule,
        messages: List<Message>,
        schema: JsonObject?,
        maxTokens: Int,
        policy: ModelPolicy,
        reasoningTokens: Int,
    ): ModelCompletion {
        runtime.track(rule.model)
        val temperature = (if (schema != null) 0.0 else rule.creativity / 100.0)
            .coerceIn(policy.minimumTemperature, policy.maximumTemperature)
        val options = OllamaChatOptions.builder().model(rule.model)
            .temperature(temperature)
            .numPredict(maxTokens + reasoningTokens).numCtx(CONTEXT_TOKENS).keepAlive(KEEP_ALIVE)
        when (policy.thinking) {
            ThinkingMode.DISABLED -> options.thinkOption(ThinkOption.ThinkBoolean.DISABLED)
            ThinkingMode.ENABLED -> options.thinkOption(ThinkOption.ThinkBoolean.ENABLED)
            ThinkingMode.LOW -> options.thinkOption(ThinkOption.ThinkLevel.LOW)
            ThinkingMode.DEFAULT -> Unit
        }
        if (schema != null) options.format(mapper.readValue(schema.toString(), Map::class.java))
        val started = System.nanoTime()
        val built = options.build()
        val attempt = tracing.current()?.begin(rule.model, messages, mapper.writeValueAsString(built))
        val response = try {
            if (attempt == null) model.call(Prompt(messages, built)) else stream(Prompt(messages, built), attempt)
        } catch (exception: RestClientResponseException) {
            throw EngineHttpFailedException(exception.statusCode.value(), exception)
        } catch (exception: WebClientResponseException) {
            throw EngineHttpFailedException(exception.statusCode.value(), exception)
        } catch (exception: WebClientException) {
            throw EngineUnavailableException(exception)
        } catch (exception: RestClientException) {
            throw EngineUnavailableException(exception)
        } catch (exception: NonTransientAiException) {
            throw EngineUnavailableException(exception)
        } catch (exception: TransientAiException) {
            throw EngineUnavailableException(exception)
        }
        val result = reader.read(response, (System.nanoTime() - started) / 1_000_000)
        attempt?.finish(result)
        return result
    }

    private fun stream(prompt: Prompt, attempt: AttemptTrace): ChatResponse {
        val text = StringBuilder()
        val thinking = StringBuilder()
        val last = AtomicReference<ChatResponse>()
        model.stream(prompt).doOnNext { chunk ->
            last.set(chunk)
            text.append(chunk.result?.output?.text.orEmpty())
            thinking.append(chunk.result?.output?.metadata?.get("thinking") as? String ?: "")
            attempt.append(chunk)
        }.blockLast(Duration.ofMinutes(3))
        val response = last.get() ?: throw EmptyModelReplyException()
        return ChatResponse(
            listOf(
                Generation(
                    AssistantMessage.builder().content(
                        text.toString(),
                    ).properties(mapOf("thinking" to thinking.toString())).build(),
                    response.result?.metadata ?: ChatGenerationMetadata.NULL,
                ),
            ),
            response.metadata,
        )
    }

    fun summarize(model: String, instruction: String, data: String): String = inference.background {
        val result = generate(
            ChatRule(model = model, creativity = MEMORY_CREATIVITY),
            listOf(SystemMessage(instruction), UserMessage(data)),
            maxTokens = MEMORY_RESPONSE_TOKENS,
        )
        if (result.text.isBlank()) throw EmptyMemorySummaryException()
        result.text.removePrefix("```markdown").removePrefix("```").removeSuffix("```").trim()
    }
}
