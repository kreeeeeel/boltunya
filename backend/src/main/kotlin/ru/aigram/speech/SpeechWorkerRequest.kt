package ru.aigram.speech

import kotlinx.serialization.Serializable

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
)
