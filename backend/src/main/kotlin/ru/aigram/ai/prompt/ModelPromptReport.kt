package ru.aigram.ai.prompt

import kotlinx.serialization.Serializable

@Serializable
data class ModelPromptReport(val prompts: List<ModelPromptSettings>)
