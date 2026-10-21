package ru.aigram.ai.model

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
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.reactive.function.client.WebClientException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import ru.aigram.ai.ModelInference
import ru.aigram.ai.OllamaRuntime
import ru.aigram.ai.profile.ModelTargetResolver
import ru.aigram.ai.profile.ProfileChatOptions
import ru.aigram.ai.profile.ProfileContextBudget
import ru.aigram.ai.profile.ProfileModelFactory
import ru.aigram.ai.prompt.ModelPromptStore
import ru.aigram.automation.activity.ReplyTraceScope
import ru.aigram.domain.ai.model.ModelCompletion
import ru.aigram.domain.ai.model.ModelMeasurement
import ru.aigram.domain.ai.model.ModelPolicy
import ru.aigram.domain.ai.model.ThinkingMode
import ru.aigram.domain.ai.profile.ModelTask
import ru.aigram.domain.ai.profile.ResolvedModelTarget
import ru.aigram.domain.chat.ChatRule
import ru.aigram.exception.AppException
import ru.aigram.exception.impl.EmptyMemorySummaryException
import ru.aigram.exception.impl.EngineHttpFailedException
import ru.aigram.exception.impl.EngineUnavailableException
import ru.aigram.exception.impl.InvalidModelReplyException
import ru.aigram.exception.impl.ModelCapabilityUnavailableException
import ru.aigram.exception.impl.ModelProviderFailedException
import ru.aigram.exception.impl.ModelProviderRefusedException
import ru.aigram.resources.ResourceCatalog
import ru.aigram.storage.ModelMeasurementStore
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

private val promptResources = ResourceCatalog()
private const val MEMORY_RESPONSE_TOKENS = 1536
private const val MAX_REASONING_TOKENS = 4096
private const val RETRY_RESPONSE_TOKENS = 512
private const val KEEP_ALIVE = "20m"
private const val MEMORY_CREATIVITY = 10

@Component
class ChatCompletionService(
    private val model: ChatModel,
    private val policies: ModelPolicies,
    private val reader: ModelReplyReader,
    private val runtime: OllamaRuntime,
    private val inference: ModelInference,
    private val measurements: ModelMeasurementStore,
    private val tracing: ReplyTraceScope = ReplyTraceScope(),
    private val responses: ModelResponseStream = ModelResponseStream(),
    private val targets: ModelTargetResolver? = null,
    private val clients: ProfileModelFactory? = null,
    private val profileOptions: ProfileChatOptions = ProfileChatOptions(),
    private val contextBudget: ProfileContextBudget = ProfileContextBudget(),
    private val promptSettings: ModelPromptStore? = null,
) {
    private val mapper = JsonMapper.builder().build()

    fun modelName(rule: ChatRule, task: ModelTask = ModelTask.REPLY): String =
        targets?.resolve(task, rule)?.model ?: rule.model

    fun promptTarget(rule: ChatRule, task: ModelTask = ModelTask.REPLY): ResolvedModelTarget {
        val original = targets?.resolve(task, rule)
            ?: ResolvedModelTarget(
                model = rule.model,
                baseUrl = "http://localhost",
                remote = false,
                automaticContext = true,
            )
        return promptSettings?.configure(original) ?: original
    }

    fun reservedTokens(target: ResolvedModelTarget, outputTokens: Int): Int = if (target.profileId.isEmpty()) {
        outputTokens + policies.get(target.model).reasoningTokens
    } else {
        contextBudget.reserve(target, outputTokens)
    }

    fun fitsSummary(model: String, instruction: String, data: String): Boolean {
        val target = promptTarget(ChatRule(model = model), ModelTask.MEMORY)
        val messages = listOf(SystemMessage(instruction), UserMessage(data))
        val reserve = if (target.profileId.isEmpty()) {
            MEMORY_RESPONSE_TOKENS + if (policies.get(target.model).reasoningTokens > 0) {
                MAX_REASONING_TOKENS
            } else {
                0
            }
        } else {
            reservedTokens(target, MEMORY_RESPONSE_TOKENS)
        }
        return contextBudget.fits(target, messages, reserve)
    }

    fun instructions(rule: ChatRule, globalPrompt: String, task: ModelTask = ModelTask.REPLY): String {
        val target = targets?.resolve(task, rule)

        if (target == null) {
            return globalPrompt
        }

        val text = promptSettings?.get(target)?.instructions.orEmpty()
        return if (text.isBlank()) {
            globalPrompt
        } else {
            globalPrompt + "\n\n" + promptResources.prompt(
                "ru/conversation/model-instructions",
                mapOf("instructions" to text),
            )
        }
    }

    fun generate(
        rule: ChatRule,
        messages: List<Message>,
        schema: JsonObject? = null,
        maxTokens: Int = rule.maxTokens,
        task: ModelTask = ModelTask.REPLY,
    ): ModelCompletion {
        val original = targets?.resolve(task, rule)
        val target = if (original == null) {
            null
        } else {
            promptSettings?.configure(original) ?: original
        }
        val group = target?.executionGroup ?: "shared"
        val action = {
            if (target != null && target.profileId.isNotEmpty()) {
                generateTarget(target, messages, schema, maxTokens, task)
            } else {
                generateLegacy(
                    if (target ==
                        null
                    ) {
                        rule
                    } else {
                        rule.copy(model = target.model)
                    },
                    messages,
                    schema,
                    maxTokens,
                    target,
                )
            }
        }
        return if (task == ModelTask.MEMORY && tracing.current()?.foregroundMemory() != true) {
            inference.background(group, action)
        } else {
            inference.foreground(group, action)
        }
    }

    fun generateTarget(
        target: ResolvedModelTarget,
        messages: List<Message>,
        schema: JsonObject? = null,
        maxTokens: Int = target.options.maxOutputTokens,
        task: ModelTask = ModelTask.REPLY,
    ): ModelCompletion {
        val factory = clients
        if (factory == null) {
            throw ModelProviderFailedException()
        }
        if (messages.stream().anyMatch { it is UserMessage && it.media.isNotEmpty() } && !target.capabilities.vision) {
            throw ModelCapabilityUnavailableException()
        }
        val configured = contextBudget.configure(target, messages, contextBudget.reserve(target, maxTokens))
        val options = profileOptions.build(configured, maxTokens, schema)
        val context = contextBudget.select(configured, messages, maxTokens)
        // Serialize an allowlist, never Spring API options (which may contain the API key).
        val safeOptions = mapper.writeValueAsString(
            mapOf(
                "profileId" to target.profileId,
                "provider" to target.provider.name,
                "task" to task.name,
                "model" to target.model,
                "parameters" to target.options,
            ),
        )
        val attempt = tracing.current()?.begin(target.model, context, safeOptions)
        val started = System.nanoTime()
        val response = try {
            val client = factory.model(target)
            val chunks = if (target.capabilities.streaming) {
                client.stream(Prompt(context, options))
            } else {
                Mono.fromCallable {
                    client.call(Prompt(context, options))
                }.subscribeOn(Schedulers.boundedElastic()).flux()
            }
            responses.read(
                chunks,
                attempt,
                Duration.ofSeconds(target.options.idleTimeoutSeconds.toLong()),
                Duration.ofSeconds(target.options.totalTimeoutSeconds.toLong()),
            )
        } catch (exception: AppException) {
            throw exception
        } catch (exception: Exception) {
            if (Thread.currentThread().isInterrupted) {
                throw exception
            }
            // API response bodies can contain echoed input or credentials; do not retain their messages as causes.
            throw ModelProviderFailedException()
        }
        val result = reader.read(response, (System.nanoTime() - started) / 1_000_000)
        attempt?.finish(result)
        if ((response.result?.output?.metadata?.get("refusal") as? String).orEmpty().isNotBlank() ||
            result.finishReason.equals("content_filter", ignoreCase = true)
        ) {
            throw ModelProviderRefusedException()
        }
        if (result.finishReason.equals("length", ignoreCase = true)) {
            throw InvalidModelReplyException()
        }
        return result
    }

    private fun generateLegacy(
        rule: ChatRule,
        messages: List<Message>,
        schema: JsonObject?,
        maxTokens: Int,
        configured: ResolvedModelTarget?,
    ): ModelCompletion {
        val target = configured ?: promptTarget(rule)
        val policy = policies.get(rule.model)
        val first = call(rule, messages, schema, maxTokens, policy, policy.reasoningTokens, target)
        val result = if (first.finishReason == "length") {
            call(
                rule,
                messages,
                schema,
                maxOf(maxTokens, RETRY_RESPONSE_TOKENS),
                policy,
                if (policy.reasoningTokens > 0) {
                    MAX_REASONING_TOKENS
                } else {
                    0
                },
                target,
            )
        } else {
            first
        }
        if (result.finishReason == "length" && result.text.isNotBlank()) {
            throw InvalidModelReplyException()
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
        target: ResolvedModelTarget,
    ): ModelCompletion {
        val configured = contextBudget.configure(target, messages, maxTokens + reasoningTokens)
        val context = contextBudget.select(configured, messages, maxTokens, maxTokens + reasoningTokens)
        runtime.track(rule.model)
        val temperature = (
            if (schema != null) {
                0.0
            } else {
                rule.creativity / 100.0
            }
            )
            .coerceIn(policy.minimumTemperature, policy.maximumTemperature)
        val options = OllamaChatOptions.builder().model(rule.model)
            .temperature(temperature)
            .numPredict(maxTokens + reasoningTokens).numCtx(configured.options.contextWindow).keepAlive(KEEP_ALIVE)
        when (policy.thinking) {
            ThinkingMode.DISABLED -> options.thinkOption(ThinkOption.ThinkBoolean.DISABLED)
            ThinkingMode.ENABLED -> options.thinkOption(ThinkOption.ThinkBoolean.ENABLED)
            ThinkingMode.LOW -> options.thinkOption(ThinkOption.ThinkLevel.LOW)
            ThinkingMode.DEFAULT -> Unit
        }
        if (schema != null) {
            options.format(mapper.readValue(schema.toString(), Map::class.java))
        }
        val started = System.nanoTime()
        val built = options.build()
        val attempt = tracing.current()?.begin(rule.model, context, mapper.writeValueAsString(built))
        val response = try {
            responses.read(model.stream(Prompt(context, built)), attempt)
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

    fun summarize(model: String, instruction: String, data: String): String {
        val result = generate(
            ChatRule(model = model, creativity = MEMORY_CREATIVITY),
            listOf(SystemMessage(instruction), UserMessage(data)),
            maxTokens = MEMORY_RESPONSE_TOKENS,
            task = ModelTask.MEMORY,
        )
        if (result.text.isBlank()) {
            throw EmptyMemorySummaryException()
        }
        return result.text.removePrefix("```markdown").removePrefix("```").removeSuffix("```").trim()
    }
}
