package ru.aigram.domain.ai.prompt

import kotlinx.serialization.Serializable

@Serializable
data class ModelPromptReport(val prompts: List<ModelPromptSettings>)
