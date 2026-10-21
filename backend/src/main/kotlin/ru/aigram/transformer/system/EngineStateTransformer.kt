package ru.aigram.transformer.system

import org.springframework.stereotype.Component
import ru.aigram.contract.dto.Engine
import ru.aigram.contract.dto.Model
import ru.aigram.contract.dto.ModelMeasurement
import ru.aigram.domain.ai.EngineStatus
import ru.aigram.domain.ai.model.ModelInfo

@Component
class EngineStateTransformer {
    fun toContract(value: EngineStatus): Engine = Engine(
        online = value.online,
        downloading = value.downloading,
        progress = value.progress,
        error = value.error,
        models = value.models.stream().map(::toContract).toList(),
        warming = value.warming,
        installed = value.installed,
        canRestart = value.canRestart,
        downloadedBytes = value.downloadedBytes,
        totalBytes = value.totalBytes,
        downloadStage = value.downloadStage,
    )

    fun toContract(value: ModelInfo): Model = Model(
        id = value.id,
        name = value.name,
        description = value.description,
        propertySize = value.size,
        installed = value.installed,
        recommended = value.recommended,
        supportsAutomation = value.supportsAutomation,
        family = value.family,
        speed = value.speed,
        quality = value.quality,
        thinking = value.thinking,
        memoryGb = value.memoryGb,
        contextTokens = value.contextTokens,
        license = value.license,
        measurement = value.measurement?.let { ModelMeasurement(it.durationMs, it.tokensPerSecond.toBigDecimal()) },
        supportsVision = value.supportsVision,
    )
}
