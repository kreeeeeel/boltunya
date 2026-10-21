package ru.aigram.ai.profile

import kotlinx.serialization.Serializable

@Serializable
data class ModelProfileReport(val profiles: List<ModelProfile>, val presets: List<ModelProviderPreset>)
