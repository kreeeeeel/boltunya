package ru.aigram.speech

import kotlinx.serialization.Serializable

@Serializable
data class SpeechLicenseManifest(val files: List<String>)
