package ru.aigram.speech

import kotlinx.serialization.Serializable
import ru.aigram.domain.speech.SpeechComputeDevice

@Serializable
data class SpeechWorkerRequest(
    val operation: String,
    val modelDirectory: String,
    val modelFile: String = "",
    val input: String = "",
    val output: String = "",
    val text: String = "",
    val language: String = "auto",
    val speed: Float = 1f,
    val runtimeDirectory: String = "",
    val indexFile: String = "",
    val conversionDevice: SpeechComputeDevice = SpeechComputeDevice.CPU,
)
