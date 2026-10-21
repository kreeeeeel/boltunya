package ru.aigram.transformer.models

import org.springframework.stereotype.Component
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
import ru.aigram.domain.ai.profile.ModelTaskRouting as DomainModelTaskRouting
import ru.aigram.domain.ai.profile.ModelThinking as DomainModelThinking
import ru.aigram.domain.ai.profile.ModelTokenLimit as DomainModelTokenLimit

@Component
class ModelProfileTransformer {
    fun toContract(value: DomainModelProfile): ModelProfile = ModelProfile(
        UUID.fromString(value.id), value.name, ModelProvider.valueOf(value.provider.name),
        ModelPreset.forValue(value.preset), value.baseUrl, value.model, value.enabled,
        toContract(value.options), toContract(value.capabilities), value.apiKeyConfigured,
    )

    fun toContract(value: DomainModelProfileReport): ModelProfileReport = ModelProfileReport(
        value.profiles.stream().map(::toContract).toList(),
        value.presets.stream().map {
            ModelProviderPreset(
                it.id,
                it.name,
                ModelProvider.valueOf(it.provider.name),
                it.baseUrl,
                it.model,
                it.apiKeyRequired,
                toContract(it.defaultsOptions),
                toContract(it.defaultCapabilities),
            )
        }.toList(),
    )

    fun toContract(value: DomainModelProfileOptions): ModelProfileOptions = ModelProfileOptions(
        stop = value.stop,
        maxOutputTokens = value.maxOutputTokens,
        contextWindow = value.contextWindow,
        reasoningTokens = value.reasoningTokens,
        reasoningEffort = value.reasoningEffort,
        thinking = ThinkingMode.valueOf(value.thinking.name),
        connectTimeoutSeconds = value.connectTimeoutSeconds,
        idleTimeoutSeconds = value.idleTimeoutSeconds,
        totalTimeoutSeconds = value.totalTimeoutSeconds,
        keepAliveMinutes = value.keepAliveMinutes,
        temperature = value.temperature?.toBigDecimal(),
        topP = value.topP?.toBigDecimal(),
        topK = value.topK,
        presencePenalty = value.presencePenalty?.toBigDecimal(),
        frequencyPenalty = value.frequencyPenalty?.toBigDecimal(),
        repeatPenalty = value.repeatPenalty?.toBigDecimal(),
        seed = value.seed,
    )

    fun toDomain(value: ModelProfileOptions): DomainModelProfileOptions = DomainModelProfileOptions(
        temperature = value.temperature?.toDouble(),
        topP = value.topP?.toDouble(),
        topK = value.topK,
        presencePenalty = value.presencePenalty?.toDouble(),
        frequencyPenalty = value.frequencyPenalty?.toDouble(),
        repeatPenalty = value.repeatPenalty?.toDouble(),
        seed = value.seed,
        stop = value.stop,
        maxOutputTokens = value.maxOutputTokens,
        contextWindow = value.contextWindow,
        reasoningTokens = value.reasoningTokens,
        reasoningEffort = value.reasoningEffort,
        thinking = DomainModelThinking.valueOf(value.thinking.name),
        connectTimeoutSeconds = value.connectTimeoutSeconds,
        idleTimeoutSeconds = value.idleTimeoutSeconds,
        totalTimeoutSeconds = value.totalTimeoutSeconds,
        keepAliveMinutes = value.keepAliveMinutes,
    )

    fun toDomain(value: ModelProfileOptionsInput): DomainModelProfileOptions = DomainModelProfileOptions(
        temperature = value.temperature?.toDouble(),
        topP = value.topP?.toDouble(),
        topK = value.topK,
        presencePenalty = value.presencePenalty?.toDouble(),
        frequencyPenalty = value.frequencyPenalty?.toDouble(),
        repeatPenalty = value.repeatPenalty?.toDouble(),
        seed = value.seed,
        stop = value.stop,
        maxOutputTokens = value.maxOutputTokens,
        contextWindow = value.contextWindow,
        reasoningTokens = value.reasoningTokens,
        reasoningEffort = value.reasoningEffort,
        thinking = DomainModelThinking.valueOf(value.thinking.name),
        connectTimeoutSeconds = value.connectTimeoutSeconds,
        idleTimeoutSeconds = value.idleTimeoutSeconds,
        totalTimeoutSeconds = value.totalTimeoutSeconds,
        keepAliveMinutes = value.keepAliveMinutes,
    )

    fun toContract(value: DomainModelCapabilities): ModelCapabilities = ModelCapabilities(
        value.streaming, value.vision, value.structuredOutput, value.reasoning, value.temperature, value.topP,
        value.topK, value.penalties, TokenLimitParameter.valueOf(value.tokenLimit.name),
    )

    fun toDomain(value: ModelCapabilities): DomainModelCapabilities = DomainModelCapabilities(
        value.streaming, value.vision, value.structuredOutput, value.reasoning, value.temperature, value.topP,
        value.topK, value.penalties, DomainModelTokenLimit.valueOf(value.tokenLimit.name),
    )

    fun toDomain(value: ModelCapabilitiesInput): DomainModelCapabilities = DomainModelCapabilities(
        value.streaming, value.vision, value.structuredOutput, value.reasoning, value.temperature, value.topP,
        value.topK, value.penalties, DomainModelTokenLimit.valueOf(value.tokenLimit.name),
    )

    fun toDomain(value: ContractModelProfileUpdate): DomainModelProfileUpdate = DomainModelProfileUpdate(
        value.name, DomainModelProvider.valueOf(value.provider.name), value.preset.value,
        value.baseUrl, value.model, value.enabled, toDomain(value.options), toDomain(value.capabilities),
        value.apiKey, value.clearApiKey,
    )

    fun toContract(value: DomainModelTaskRouting): ModelTaskRouting = ModelTaskRouting(
        value.enabled, value.parallelIndependentTasks, value.replyProfileId, value.memoryProfileId,
        value.researchProfileId, value.visionProfileId, value.replyModel, value.memoryModel, value.researchModel,
        value.visionModel,
    )

    fun toDomain(value: ModelTaskRouting): DomainModelTaskRouting = DomainModelTaskRouting(
        value.enabled, value.parallelIndependentTasks, value.replyProfileId, value.memoryProfileId,
        value.researchProfileId, value.visionProfileId, value.replyModel.orEmpty(), value.memoryModel.orEmpty(),
        value.researchModel.orEmpty(), value.visionModel.orEmpty(),
    )

    fun toContract(value: DomainModelProfileTestResult): ModelProfileTestResult = ModelProfileTestResult(
        value.ok,
        value.code,
        value.message,
        value.durationMs,
        value.inputTokens,
        value.outputTokens,
        value.finishReason,
        value.preview,
    )
}
