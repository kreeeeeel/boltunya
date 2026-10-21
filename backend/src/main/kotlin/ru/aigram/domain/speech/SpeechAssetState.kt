package ru.aigram.domain.speech

import kotlinx.serialization.Serializable

@Serializable
data class SpeechAssetState(val assets: List<SpeechAsset>, val downloads: List<SpeechDownload>)
