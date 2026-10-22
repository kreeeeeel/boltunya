package ru.aigram.transformer.speech

import ru.aigram.contract.dto.GeneratedVoice
import ru.aigram.contract.dto.SpeechAsset
import ru.aigram.contract.dto.SpeechAssetKind
import ru.aigram.contract.dto.SpeechDownload
import ru.aigram.contract.dto.SpeechDownloadPhase
import ru.aigram.contract.dto.SpeechEngineKind
import ru.aigram.contract.dto.SpeechState
import ru.aigram.domain.speech.SpeechAssetState
import ru.aigram.contract.dto.SpeechComputeDevice as ContractSpeechComputeDevice
import ru.aigram.contract.dto.SpeechComputeReason as ContractSpeechComputeReason
import ru.aigram.contract.dto.SpeechComputeStatus as ContractSpeechComputeStatus
import ru.aigram.domain.speech.GeneratedVoice as DomainGeneratedVoice
import ru.aigram.domain.speech.SpeechComputeStatus as DomainSpeechComputeStatus

fun DomainSpeechComputeStatus.toContract(): ContractSpeechComputeStatus = ContractSpeechComputeStatus(
    selectedDevice = ContractSpeechComputeDevice.valueOf(selectedDevice.name),
    runtimeInstalled = runtimeInstalled,
    cudaAvailable = cudaAvailable,
    gpuName = gpuName,
    gpuMemoryBytes = gpuMemoryBytes,
    torchVersion = torchVersion,
    cudaVersion = cudaVersion,
    reason = ContractSpeechComputeReason.valueOf(reason.name),
)

fun DomainGeneratedVoice.toContract(): GeneratedVoice = GeneratedVoice(id, url, mimeType, duration, waveform)

fun SpeechAssetState.toContract(): SpeechState = SpeechState(
    assets.stream().map { asset ->
        SpeechAsset(
            id = asset.id,
            title = asset.title,
            kind = SpeechAssetKind.valueOf(asset.kind.name),
            languages = asset.languages,
            sizeBytes = asset.sizeBytes,
            license = asset.license,
            sourceUrl = asset.sourceUrl,
            installed = asset.installed,
            dependencies = asset.dependencies,
            userProvided = asset.userProvided,
            engine = SpeechEngineKind.valueOf(asset.engine.name),
            format = asset.format,
        )
    }.toList(),
    downloads.stream().map { download ->
        SpeechDownload(
            download.assetId,
            SpeechDownloadPhase.valueOf(download.phase.name),
            download.downloadedBytes,
            download.totalBytes,
            download.error,
        )
    }.toList(),
)
