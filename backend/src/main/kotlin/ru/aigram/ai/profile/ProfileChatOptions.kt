package ru.aigram.ai.profile

import jakarta.inject.Inject
import kotlinx.serialization.json.JsonObject
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.ollama.api.OllamaChatOptions
import org.springframework.ai.ollama.api.ThinkOption
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.stereotype.Component
import ru.aigram.exception.impl.ModelCapabilityUnavailableException
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

/** Projects only parameters explicitly supported by the selected model. */
@Component
class ProfileChatOptions @Inject constructor() {
    private val mapper = JsonMapper.builder().build()

    fun build(target: ResolvedModelTarget, maxTokens: Int, schema: JsonObject?): ChatOptions {
        val options = target.options
        val capabilities = target.capabilities
        if (schema != null && !capabilities.structuredOutput) throw ModelCapabilityUnavailableException()
        val tokens = minOf(
            options.maxOutputTokens,
            maxTokens + if (capabilities.reasoning) options.reasoningTokens else 0,
        )
        if (target.provider == ModelProvider.OLLAMA) {
            val built = OllamaChatOptions.builder().model(target.model).numPredict(tokens)
                .numCtx(options.contextWindow).keepAlive("${options.keepAliveMinutes}m")
            if (capabilities.temperature && options.temperature != null) built.temperature(options.temperature)
            if (capabilities.topP && options.topP != null) built.topP(options.topP)
            if (capabilities.topK && options.topK != null) built.topK(options.topK)
            if (capabilities.penalties && options.repeatPenalty != null) built.repeatPenalty(options.repeatPenalty)
            if (options.seed != null) built.seed(options.seed)
            if (options.stop.isNotEmpty()) built.stop(options.stop)
            when (options.thinking) {
                ModelThinking.DISABLED -> built.thinkOption(ThinkOption.ThinkBoolean.DISABLED)
                ModelThinking.ENABLED -> built.thinkOption(ThinkOption.ThinkBoolean.ENABLED)
                ModelThinking.DEFAULT -> Unit
            }
            if (schema != null) built.format(mapper.readValue(schema.toString(), Map::class.java))
            return built.build()
        }
        val built = OpenAiChatOptions.builder().model(target.model).maxRetries(0)
            .timeout(Duration.ofSeconds(options.totalTimeoutSeconds.toLong()))
            .streamUsage(true)
        when (capabilities.tokenLimit) {
            ModelTokenLimit.MAX_TOKENS -> built.maxTokens(tokens)
            ModelTokenLimit.MAX_COMPLETION_TOKENS -> built.maxCompletionTokens(tokens)
        }
        if (capabilities.temperature && options.temperature != null) built.temperature(options.temperature)
        if (capabilities.topP && options.topP != null) built.topP(options.topP)
        if (capabilities.penalties) {
            if (options.presencePenalty != null) built.presencePenalty(options.presencePenalty)
            if (options.frequencyPenalty != null) built.frequencyPenalty(options.frequencyPenalty)
        }
        if (options.seed != null) built.seed(options.seed)
        if (options.stop.isNotEmpty()) built.stopSequences(options.stop)
        if (capabilities.reasoning &&
            options.reasoningEffort != "default"
        ) {
            built.reasoningEffort(options.reasoningEffort)
        }
        if (schema != null) {
            built.responseFormat(
                OpenAiChatModel.ResponseFormat.builder().jsonSchema(schema.toString()).strict(true).build(),
            )
        }
        return built.build()
    }
}
