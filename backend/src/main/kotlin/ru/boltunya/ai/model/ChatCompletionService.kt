package ru.boltunya.ai.model

import jakarta.inject.Inject
import kotlinx.serialization.json.JsonObject
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.ollama.api.OllamaChatOptions
import org.springframework.ai.ollama.api.ThinkOption
import org.springframework.ai.retry.NonTransientAiException
import org.springframework.ai.retry.TransientAiException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import ru.boltunya.ai.ModelInference
import ru.boltunya.ai.OllamaRuntime
import ru.boltunya.domain.ChatRule
import ru.boltunya.domain.ModelMeasurement
import ru.boltunya.exception.impl.EmptyMemorySummaryException
import ru.boltunya.exception.impl.EngineHttpFailedException
import ru.boltunya.exception.impl.EngineUnavailableException
import ru.boltunya.storage.ModelMeasurementStore
import tools.jackson.databind.json.JsonMapper

private const val CONTEXT_TOKENS = 8192
private const val MEMORY_RESPONSE_TOKENS = 1536
private const val MAX_REASONING_TOKENS = 4096
private const val KEEP_ALIVE = "20m"
private const val REPETITION_PENALTY = 1.05
private const val TOP_P = 0.8
private const val TOP_K = 20
private const val MEMORY_CREATIVITY = 10

class ChatCompletionService @Inject constructor(
    private val model: ChatModel,
    private val policies: ModelPolicies,
    private val reader: ModelReplyReader,
    private val runtime: OllamaRuntime,
    private val inference: ModelInference,
    private val measurements: ModelMeasurementStore,
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
        val result = if (first.text.isBlank() && first.finishReason == "length") {
            call(rule, messages, schema, maxTokens, policy, MAX_REASONING_TOKENS)
        } else {
            first
        }
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
        val options = OllamaChatOptions.builder().model(rule.model)
            .temperature(if (schema != null) 0.0 else rule.creativity / 100.0)
            .numPredict(maxTokens + reasoningTokens).numCtx(CONTEXT_TOKENS).keepAlive(KEEP_ALIVE)
            .repeatPenalty(REPETITION_PENALTY).topP(TOP_P).topK(TOP_K)
        when (policy.thinking) {
            ThinkingMode.DISABLED -> options.thinkOption(ThinkOption.ThinkBoolean.DISABLED)
            ThinkingMode.LOW -> options.thinkOption(ThinkOption.ThinkLevel.LOW)
            ThinkingMode.DEFAULT -> Unit
        }
        if (schema != null) options.format(mapper.readValue(schema.toString(), Map::class.java))
        val started = System.nanoTime()
        val response = try {
            model.call(Prompt(messages, options.build()))
        } catch (exception: RestClientResponseException) {
            throw EngineHttpFailedException(exception.statusCode.value(), exception)
        } catch (exception: RestClientException) {
            throw EngineUnavailableException(exception)
        } catch (exception: NonTransientAiException) {
            throw EngineUnavailableException(exception)
        } catch (exception: TransientAiException) {
            throw EngineUnavailableException(exception)
        }
        return reader.read(response, (System.nanoTime() - started) / 1_000_000)
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
