package ru.aigram.component.models

import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.stereotype.Component
import ru.aigram.ai.ModelCatalog
import ru.aigram.ai.PromptBuilder
import ru.aigram.ai.model.ModelPolicies
import ru.aigram.ai.profile.ModelTargetResolver
import ru.aigram.ai.profile.ProfileContextBudget
import ru.aigram.ai.prompt.ModelPromptStore
import ru.aigram.ai.prompt.PromptTokenEstimator
import ru.aigram.domain.ai.profile.ModelTask
import ru.aigram.domain.ai.profile.ResolvedModelTarget
import ru.aigram.domain.ai.prompt.ModelPromptReport
import ru.aigram.domain.ai.prompt.ModelPromptSettings
import ru.aigram.domain.ai.prompt.PromptBudgetReport
import ru.aigram.domain.ai.prompt.PromptBudgetRequest
import ru.aigram.domain.chat.ChatRule
import ru.aigram.exception.impl.InvalidModelPromptException
import ru.aigram.resources.ResourceCatalog
import ru.aigram.storage.SettingsStore
import ru.aigram.validation.InputValidation

@Component
class ModelPromptsComponent(
    private val preferences: ModelPromptStore,
    private val settings: SettingsStore,
    private val targets: ModelTargetResolver,
    private val models: ModelCatalog,
    private val validation: InputValidation,
    private val prompts: PromptBuilder,
    private val tokens: PromptTokenEstimator,
    private val budget: ProfileContextBudget,
    private val policies: ModelPolicies,
    private val resources: ResourceCatalog,
) {
    fun list(): ModelPromptReport = ModelPromptReport(preferences.list())

    fun save(submitted: ModelPromptSettings): ModelPromptSettings {
        val rule = ChatRule(model = submitted.model, modelProfileId = submitted.profileId, useDefaultModel = false)
        validation.target(rule)
        validate(submitted, targets.resolve(ModelTask.REPLY, rule))

        return preferences.save(submitted)
    }

    fun budget(request: PromptBudgetRequest): PromptBudgetReport {
        validation.validate(request)
        val rule = settings.resolve(settings.resolveModel(request.rule))
        validation.target(rule)
        val original = targets.resolve(ModelTask.REPLY, rule)
        val selected = request.preferences ?: preferences.get(original)
        validate(selected, original)
        val configured = preferences.configure(original, selected)
        val global = request.globalPrompt ?: settings.get().globalPrompt
        val ownerInstructions = if (selected.instructions.isBlank()) {
            global
        } else {
            global + "\n\n" + resources.prompt(
                "ru/conversation/model-instructions",
                mapOf("instructions" to selected.instructions),
            )
        }
        val messages = prompts.build(rule, emptyList(), ownerInstructions).stream()
            .filter { it is SystemMessage }.map { it as SystemMessage }.toList()
        val reserve = if (configured.profileId.isEmpty()) {
            rule.maxTokens + policies.get(configured.model).reasoningTokens
        } else {
            budget.reserve(configured, rule.maxTokens)
        }
        val target = budget.configure(configured, messages, reserve)
        val system = messages.stream().mapToInt { tokens.estimate(it, target.model, target.tokenEncoding) }.sum()
        return PromptBudgetReport(
            target.model, target.profileId, target.options.contextWindow, maximum(target), system, reserve,
            (target.options.contextWindow - system - reserve).coerceAtLeast(0),
            system + reserve < target.options.contextWindow,
            tokens.encoding(target.model, target.tokenEncoding), selected,
            automaticContext = target.automaticContext,
        )
    }

    private fun validate(value: ModelPromptSettings, target: ResolvedModelTarget) {
        validation.validate(value)
        if (value.model != target.model || value.profileId != target.profileId ||
            value.contextWindow != null && (target.profileId.isNotEmpty() || value.contextWindow > maximum(target))
        ) {
            throw InvalidModelPromptException()
        }
    }

    private fun maximum(target: ResolvedModelTarget): Int = if (target.profileId.isEmpty()) {
        models.info(target.model)?.contextTokens?.takeIf { it > 0 } ?: target.options.contextWindow
    } else {
        target.options.contextWindow
    }
}
