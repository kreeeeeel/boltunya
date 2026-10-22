package ru.aigram.domain.speech

data class SpeechComputeStatus(
    val selectedDevice: SpeechComputeDevice,
    val runtimeInstalled: Boolean,
    val cudaAvailable: Boolean,
    val gpuName: String = "",
    val gpuMemoryBytes: Long = 0,
    val torchVersion: String = "",
    val cudaVersion: String = "",
    val reason: SpeechComputeReason,
)
