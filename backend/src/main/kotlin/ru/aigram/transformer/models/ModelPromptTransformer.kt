package ru.aigram.transformer.models

import ru.aigram.contract.dto.ModelPromptReport
import ru.aigram.contract.dto.ModelPromptSettings
import ru.aigram.contract.dto.ModelPromptSettingsInput
import ru.aigram.contract.dto.PromptBudgetReport
import ru.aigram.contract.dto.TokenEncoding
import ru.aigram.domain.chat.ChatRule
import ru.aigram.transformer.chat.toDomain
import ru.aigram.contract.dto.PromptBudgetRequest as ContractPromptBudgetRequest
import ru.aigram.domain.ai.prompt.ModelPromptReport as DomainModelPromptReport
import ru.aigram.domain.ai.prompt.ModelPromptSettings as DomainModelPromptSettings
import ru.aigram.domain.ai.prompt.PromptBudgetReport as DomainPromptBudgetReport
import ru.aigram.domain.ai.prompt.PromptBudgetRequest as DomainPromptBudgetRequest
import ru.aigram.domain.ai.prompt.TokenEncoding as DomainTokenEncoding

fun DomainModelPromptSettings.toContract(): ModelPromptSettings = ModelPromptSettings(
    model,
    profileId,
    instructions,
    contextWindow,
    TokenEncoding.valueOf(tokenEncoding.name),
)

fun ModelPromptSettingsInput.toDomain(): DomainModelPromptSettings = DomainModelPromptSettings(
    model,
    profileId,
    instructions,
    contextWindow,
    DomainTokenEncoding.valueOf(tokenEncoding.name),
)

fun DomainModelPromptReport.toContract(): ModelPromptReport = ModelPromptReport(
    prompts.stream().map { it.toContract() }.toList(),
)

fun ContractPromptBudgetRequest.toDomain(): DomainPromptBudgetRequest = DomainPromptBudgetRequest(
    rule?.toDomain() ?: ChatRule(),
    globalPrompt,
    preferences?.toDomain(),
)

fun DomainPromptBudgetReport.toContract(): PromptBudgetReport = PromptBudgetReport(
    model, profileId, contextWindow, maxContextTokens, systemTokens,
    reservedOutputTokens, remainingTokens, fits, TokenEncoding.valueOf(tokenEncoding.name),
    preferences.toContract(), estimated, includesHistory, automaticContext,
)
