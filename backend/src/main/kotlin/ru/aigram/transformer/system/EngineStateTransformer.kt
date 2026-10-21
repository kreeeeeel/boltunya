package ru.aigram.transformer.system

import ru.aigram.contract.dto.Engine
import ru.aigram.contract.dto.Model
import ru.aigram.contract.dto.ModelMeasurement
import ru.aigram.domain.ai.EngineStatus
import ru.aigram.domain.ai.model.ModelInfo

fun EngineStatus.toContract(): Engine = Engine(
    online = online,
    downloading = downloading,
    progress = progress,
    error = error,
    models = models.stream().map { it.toContract() }.toList(),
    warming = warming,
    installed = installed,
    canRestart = canRestart,
    downloadedBytes = downloadedBytes,
    totalBytes = totalBytes,
    downloadStage = downloadStage,
)

fun ModelInfo.toContract(): Model = Model(
    id = id,
    name = name,
    description = description,
    propertySize = size,
    installed = installed,
    recommended = recommended,
    supportsAutomation = supportsAutomation,
    family = family,
    speed = speed,
    quality = quality,
    thinking = thinking,
    memoryGb = memoryGb,
    contextTokens = contextTokens,
    license = license,
    measurement = measurement?.let { ModelMeasurement(it.durationMs, it.tokensPerSecond.toBigDecimal()) },
    supportsVision = supportsVision,
)
