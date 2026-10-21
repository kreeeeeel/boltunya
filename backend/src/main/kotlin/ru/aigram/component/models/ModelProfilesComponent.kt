package ru.aigram.component.models

import org.springframework.ai.chat.messages.UserMessage
import org.springframework.stereotype.Component
import ru.aigram.ai.ModelInference
import ru.aigram.ai.model.ChatCompletionService
import ru.aigram.ai.profile.ModelProfileStore
import ru.aigram.ai.profile.ModelRoutingStore
import ru.aigram.ai.profile.ModelTargetResolver
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ai.profile.ModelProfile
import ru.aigram.domain.ai.profile.ModelProfileReport
import ru.aigram.domain.ai.profile.ModelProfileTestResult
import ru.aigram.domain.ai.profile.ModelProfileUpdate
import ru.aigram.domain.ai.profile.ModelTask
import ru.aigram.domain.ai.profile.ModelTaskRouting
import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode
import ru.aigram.exception.impl.InvalidModelProfileException
import ru.aigram.exception.impl.ModelCapabilityUnavailableException
import ru.aigram.exception.impl.ModelProfileInUseException
import ru.aigram.exception.impl.ModelProviderFailedException
import ru.aigram.exception.impl.ModelTestConfirmationRequiredException
import ru.aigram.resources.ResourceCatalog
import ru.aigram.storage.SettingsStore
import ru.aigram.validation.InputValidation

private const val TEST_MAX_TOKENS = 32
private const val MAX_PREVIEW_CHARACTERS = 300
private const val NANOS_PER_MILLISECOND = 1_000_000

@Component
class ModelProfilesComponent(
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

    fun save(id: String?, update: ModelProfileUpdate): ModelProfile {
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

    fun routing(update: ModelTaskRouting): ModelTaskRouting {
        validation.validate(update)

        ModelTask.entries.stream().forEach { task ->
            val id = update.profileId(task)
            val model = update.model(task)
            if (id.isNotEmpty() && model.isNotEmpty()) {
                throw InvalidModelProfileException()
            }
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
                if (task == ModelTask.VISION && !target.capabilities.vision) {
                    throw ModelCapabilityUnavailableException()
                }
            }
        }

        replies.cancelPending()
        return routing.save(update)
    }

    fun test(id: String, confirmGeneration: Boolean): ModelProfileTestResult {
        if (!confirmGeneration) {
            throw ModelTestConfirmationRequiredException()
        }
        val target = targets.profile(id)
        val started = System.nanoTime()
        return try {
            val result = inference.foreground {
                completion.generateTarget(
                    target,
                    listOf(UserMessage(resources.prompt("common/provider-test"))),
                    maxTokens = TEST_MAX_TOKENS,
                )
            }
            ModelProfileTestResult(
                result.text.isNotBlank(),
                if (result.text.isBlank()) {
                    ErrorCode.EMPTY_MODEL_REPLY.name
                } else {
                    ""
                },
                durationMs = result.durationMs,
                inputTokens = result.inputTokens,
                outputTokens = result.outputTokens,
                finishReason = result.finishReason,
                preview = result.text.take(MAX_PREVIEW_CHARACTERS),
            )
        } catch (exception: AppException) {
            ModelProfileTestResult(false, exception.code.name, exception.message, elapsed(started))
        } catch (_: Exception) {
            val error = ModelProviderFailedException()
            ModelProfileTestResult(false, error.code.name, error.message, elapsed(started))
        }
    }

    private fun elapsed(started: Long): Long = (System.nanoTime() - started) / NANOS_PER_MILLISECOND
}
