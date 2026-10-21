package ru.aigram.http.endpoint

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.ai.chat.messages.UserMessage
import ru.aigram.ai.ModelInference
import ru.aigram.ai.model.ChatCompletionService
import ru.aigram.ai.profile.ModelProfile
import ru.aigram.ai.profile.ModelProfileReport
import ru.aigram.ai.profile.ModelProfileStore
import ru.aigram.ai.profile.ModelProfileTestResult
import ru.aigram.ai.profile.ModelProfileUpdate
import ru.aigram.ai.profile.ModelRoutingStore
import ru.aigram.ai.profile.ModelTargetResolver
import ru.aigram.ai.profile.ModelTask
import ru.aigram.ai.profile.ModelTaskRouting
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.exception.AppException
import ru.aigram.exception.impl.InvalidModelProfileException
import ru.aigram.exception.impl.ModelCapabilityUnavailableException
import ru.aigram.exception.impl.ModelProfileInUseException
import ru.aigram.exception.impl.ModelProviderFailedException
import ru.aigram.exception.impl.ModelTestConfirmationRequiredException
import ru.aigram.http.InputValidation
import ru.aigram.resources.ResourceCatalog
import ru.aigram.storage.SettingsStore

class ModelProfilesEndpoint @Inject constructor(
    private val json: Json,
    private val profiles: ModelProfileStore,
    private val routing: ModelRoutingStore,
    private val settings: SettingsStore,
    private val validation: InputValidation,
    private val targets: ModelTargetResolver,
    private val completion: ChatCompletionService,
    private val inference: ModelInference,
    private val resources: ResourceCatalog,
    private val replies: ReplyCoordinator,
) {
    fun list(): ModelProfileReport = ModelProfileReport(profiles.list(), profiles.presets())

    fun save(id: String?, body: JsonObject): ModelProfile {
        val update = json.decodeFromJsonElement<ModelProfileUpdate>(body)
        validation.validate(update)
        val saved = profiles.save(id, update)
        replies.cancelPending()
        return saved
    }

    fun delete(id: String) {
        profiles.get(id)
        val current = settings.get()
        if (current.defaultModelProfileId == id || current.rules.values.stream().anyMatch { it.modelProfileId == id } ||
            ModelTask.entries.stream().anyMatch { routing.get().profileId(it) == id }
        ) {
            throw ModelProfileInUseException()
        }
        profiles.delete(id)
    }

    fun routing(): ModelTaskRouting = routing.get()

    fun routing(body: JsonObject): ModelTaskRouting {
        val update = json.decodeFromJsonElement<ModelTaskRouting>(body)
        validation.validate(update)
        ModelTask.entries.stream().forEach { task ->
            val id = update.profileId(task)
            val model = update.model(task)
            if (id.isNotEmpty() && model.isNotEmpty()) throw InvalidModelProfileException()
            if (model.isNotEmpty()) {
                if (!model.codePoints().allMatch {
                        Character.isLetterOrDigit(it) || it.toChar() in "-_.:/"
                    }
                ) {
                    throw InvalidModelProfileException()
                }
                if (task == ModelTask.VISION && !targets.localModel(model).capabilities.vision) {
                    throw ModelCapabilityUnavailableException()
                }
            }
            if (id.isNotEmpty()) {
                val target = targets.profile(id)
                if (task == ModelTask.VISION && !target.capabilities.vision) throw ModelCapabilityUnavailableException()
            }
        }
        replies.cancelPending()
        return routing.save(update)
    }

    fun test(id: String, body: JsonObject): ModelProfileTestResult {
        if (body["confirmGeneration"]?.jsonPrimitive?.booleanOrNull !=
            true
        ) {
            throw ModelTestConfirmationRequiredException()
        }
        val target = targets.profile(id)
        val started = System.nanoTime()
        return try {
            val result = inference.foreground {
                completion.generateTarget(
                    target,
                    listOf(UserMessage(resources.prompt("common/provider-test"))),
                    maxTokens = 32,
                )
            }
            ModelProfileTestResult(
                result.text.isNotBlank(),
                if (result.text.isBlank()) "EMPTY_MODEL_REPLY" else "",
                durationMs = result.durationMs,
                inputTokens = result.inputTokens,
                outputTokens = result.outputTokens,
                finishReason = result.finishReason,
                preview = result.text.take(300),
            )
        } catch (exception: AppException) {
            ModelProfileTestResult(false, exception.code.name, exception.message, elapsed(started))
        } catch (_: Exception) {
            val error = ModelProviderFailedException()
            ModelProfileTestResult(false, error.code.name, error.message, elapsed(started))
        }
    }

    private fun elapsed(started: Long): Long = (System.nanoTime() - started) / 1_000_000
}
