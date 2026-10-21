package ru.aigram.transformer.models

import org.springframework.stereotype.Component
import ru.aigram.contract.dto.ModelPromptReport
import ru.aigram.contract.dto.ModelPromptSettings
import ru.aigram.contract.dto.ModelPromptSettingsInput
import ru.aigram.contract.dto.PromptBudgetReport
import ru.aigram.contract.dto.TokenEncoding
import ru.aigram.domain.chat.ChatRule
import ru.aigram.transformer.chat.ChatRuleTransformer
import ru.aigram.contract.dto.PromptBudgetRequest as ContractPromptBudgetRequest
import ru.aigram.domain.ai.prompt.ModelPromptReport as DomainModelPromptReport
import ru.aigram.domain.ai.prompt.ModelPromptSettings as DomainModelPromptSettings
import ru.aigram.domain.ai.prompt.PromptBudgetReport as DomainPromptBudgetReport
import ru.aigram.domain.ai.prompt.PromptBudgetRequest as DomainPromptBudgetRequest
import ru.aigram.domain.ai.prompt.TokenEncoding as DomainTokenEncoding

@Component
class ModelPromptTransformer(private val rules: ChatRuleTransformer) {
    fun toContract(value: DomainModelPromptSettings): ModelPromptSettings = ModelPromptSettings(
        value.model,
        value.profileId,
        value.instructions,
        value.contextWindow,
        TokenEncoding.valueOf(value.tokenEncoding.name),
    )

    fun toDomain(value: ModelPromptSettingsInput): DomainModelPromptSettings = DomainModelPromptSettings(
        value.model,
        value.profileId,
        value.instructions,
        value.contextWindow,
        DomainTokenEncoding.valueOf(value.tokenEncoding.name),
    )

    fun toContract(value: DomainModelPromptReport): ModelPromptReport = ModelPromptReport(
        value.prompts.stream().map(::toContract).toList(),
    )

    fun toDomain(value: ContractPromptBudgetRequest): DomainPromptBudgetRequest = DomainPromptBudgetRequest(
        value.rule?.let(rules::toDomain) ?: ChatRule(),
        value.globalPrompt,
        value.preferences?.let(::toDomain),
    )

    fun toContract(value: DomainPromptBudgetReport): PromptBudgetReport = PromptBudgetReport(
        value.model, value.profileId, value.contextWindow, value.maxContextTokens, value.systemTokens,
        value.reservedOutputTokens, value.remainingTokens, value.fits, TokenEncoding.valueOf(value.tokenEncoding.name),
        toContract(value.preferences), value.estimated, value.includesHistory, value.automaticContext,
    )
}
