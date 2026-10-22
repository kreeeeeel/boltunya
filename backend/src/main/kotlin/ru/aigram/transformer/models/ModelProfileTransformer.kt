package ru.aigram.transformer.models

import ru.aigram.contract.dto.ModelCapabilities
import ru.aigram.contract.dto.ModelCapabilitiesInput
import ru.aigram.contract.dto.ModelPreset
import ru.aigram.contract.dto.ModelProfile
import ru.aigram.contract.dto.ModelProfileOptions
import ru.aigram.contract.dto.ModelProfileOptionsInput
import ru.aigram.contract.dto.ModelProfileReport
import ru.aigram.contract.dto.ModelProfileTestResult
import ru.aigram.contract.dto.ModelProvider
import ru.aigram.contract.dto.ModelProviderPreset
import ru.aigram.contract.dto.ModelTaskRouting
import ru.aigram.contract.dto.ThinkingMode
import ru.aigram.contract.dto.TokenLimitParameter
import java.util.UUID
import ru.aigram.contract.dto.ModelProfileUpdate as ContractModelProfileUpdate
import ru.aigram.domain.ai.profile.ModelCapabilities as DomainModelCapabilities
import ru.aigram.domain.ai.profile.ModelProfile as DomainModelProfile
import ru.aigram.domain.ai.profile.ModelProfileOptions as DomainModelProfileOptions
import ru.aigram.domain.ai.profile.ModelProfileReport as DomainModelProfileReport
import ru.aigram.domain.ai.profile.ModelProfileTestResult as DomainModelProfileTestResult
import ru.aigram.domain.ai.profile.ModelProfileUpdate as DomainModelProfileUpdate
import ru.aigram.domain.ai.profile.ModelProvider as DomainModelProvider
import ru.aigram.domain.ai.profile.ModelProviderPreset as DomainModelProviderPreset
import ru.aigram.domain.ai.profile.ModelTaskRouting as DomainModelTaskRouting
import ru.aigram.domain.ai.profile.ModelThinking as DomainModelThinking
import ru.aigram.domain.ai.profile.ModelTokenLimit as DomainModelTokenLimit

fun DomainModelProfile.toContract(): ModelProfile = ModelProfile(
    UUID.fromString(id), name, ModelProvider.valueOf(provider.name),
    ModelPreset.forValue(preset), baseUrl, model, enabled,
    options.toContract(), capabilities.toContract(), apiKeyConfigured,
)

fun DomainModelProfileReport.toContract(): ModelProfileReport = ModelProfileReport(
    profiles.stream().map { it.toContract() }.toList(),
    presets.stream().map { it.toContract() }.toList(),
)

fun DomainModelProviderPreset.toContract(): ModelProviderPreset = ModelProviderPreset(
    id,
    name,
    ModelProvider.valueOf(provider.name),
    baseUrl,
    model,
    apiKeyRequired,
    defaultsOptions.toContract(),
    defaultCapabilities.toContract(),
)

fun DomainModelProfileOptions.toContract(): ModelProfileOptions = ModelProfileOptions(
    stop = stop,
    maxOutputTokens = maxOutputTokens,
    contextWindow = contextWindow,
    reasoningTokens = reasoningTokens,
    reasoningEffort = reasoningEffort,
    thinking = ThinkingMode.valueOf(thinking.name),
    connectTimeoutSeconds = connectTimeoutSeconds,
    idleTimeoutSeconds = idleTimeoutSeconds,
    totalTimeoutSeconds = totalTimeoutSeconds,
    keepAliveMinutes = keepAliveMinutes,
    temperature = temperature?.toBigDecimal(),
    topP = topP?.toBigDecimal(),
    topK = topK,
    presencePenalty = presencePenalty?.toBigDecimal(),
    frequencyPenalty = frequencyPenalty?.toBigDecimal(),
    repeatPenalty = repeatPenalty?.toBigDecimal(),
    seed = seed,
)

fun ModelProfileOptions.toDomain(): DomainModelProfileOptions = DomainModelProfileOptions(
    temperature = temperature?.toDouble(),
    topP = topP?.toDouble(),
    topK = topK,
    presencePenalty = presencePenalty?.toDouble(),
    frequencyPenalty = frequencyPenalty?.toDouble(),
    repeatPenalty = repeatPenalty?.toDouble(),
    seed = seed,
    stop = stop,
    maxOutputTokens = maxOutputTokens,
    contextWindow = contextWindow,
    reasoningTokens = reasoningTokens,
    reasoningEffort = reasoningEffort,
    thinking = DomainModelThinking.valueOf(thinking.name),
    connectTimeoutSeconds = connectTimeoutSeconds,
    idleTimeoutSeconds = idleTimeoutSeconds,
    totalTimeoutSeconds = totalTimeoutSeconds,
    keepAliveMinutes = keepAliveMinutes,
)

fun ModelProfileOptionsInput.toDomain(): DomainModelProfileOptions = DomainModelProfileOptions(
    temperature = temperature?.toDouble(),
    topP = topP?.toDouble(),
    topK = topK,
    presencePenalty = presencePenalty?.toDouble(),
    frequencyPenalty = frequencyPenalty?.toDouble(),
    repeatPenalty = repeatPenalty?.toDouble(),
    seed = seed,
    stop = stop,
    maxOutputTokens = maxOutputTokens,
    contextWindow = contextWindow,
    reasoningTokens = reasoningTokens,
    reasoningEffort = reasoningEffort,
    thinking = DomainModelThinking.valueOf(thinking.name),
    connectTimeoutSeconds = connectTimeoutSeconds,
    idleTimeoutSeconds = idleTimeoutSeconds,
    totalTimeoutSeconds = totalTimeoutSeconds,
    keepAliveMinutes = keepAliveMinutes,
)

fun DomainModelCapabilities.toContract(): ModelCapabilities = ModelCapabilities(
    streaming, vision, structuredOutput, reasoning, temperature, topP,
    topK, penalties, TokenLimitParameter.valueOf(tokenLimit.name),
)

fun ModelCapabilities.toDomain(): DomainModelCapabilities = DomainModelCapabilities(
    streaming, vision, structuredOutput, reasoning, temperature, topP,
    topK, penalties, DomainModelTokenLimit.valueOf(tokenLimit.name),
)

fun ModelCapabilitiesInput.toDomain(): DomainModelCapabilities = DomainModelCapabilities(
    streaming, vision, structuredOutput, reasoning, temperature, topP,
    topK, penalties, DomainModelTokenLimit.valueOf(tokenLimit.name),
)

fun ContractModelProfileUpdate.toDomain(): DomainModelProfileUpdate = DomainModelProfileUpdate(
    name, DomainModelProvider.valueOf(provider.name), preset.value,
    baseUrl, model, enabled, options.toDomain(), capabilities.toDomain(),
    apiKey, clearApiKey,
)

fun DomainModelTaskRouting.toContract(): ModelTaskRouting = ModelTaskRouting(
    enabled,
    parallelIndependentTasks,
    replyProfileId,
    memoryProfileId,
    visionProfileId,
    replyModel,
    memoryModel,
    visionModel,
)

fun ModelTaskRouting.toDomain(): DomainModelTaskRouting = DomainModelTaskRouting(
    enabled,
    parallelIndependentTasks,
    replyProfileId,
    memoryProfileId,
    visionProfileId,
    replyModel.orEmpty(),
    memoryModel.orEmpty(),
    visionModel.orEmpty(),
)

fun DomainModelProfileTestResult.toContract(): ModelProfileTestResult = ModelProfileTestResult(
    ok,
    code,
    message,
    durationMs,
    inputTokens,
    outputTokens,
    finishReason,
    preview,
)
