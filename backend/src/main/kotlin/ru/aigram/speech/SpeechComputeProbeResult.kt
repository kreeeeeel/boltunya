package ru.aigram.speech

import kotlinx.serialization.Serializable

@Serializable
data class SpeechComputeProbeResult(
    val cudaAvailable: Boolean,
    val gpuName: String,
    val gpuMemoryBytes: Long,
    val torchVersion: String,
    val cudaVersion: String,
)
