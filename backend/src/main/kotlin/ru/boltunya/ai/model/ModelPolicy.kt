package ru.boltunya.ai.model

import kotlinx.serialization.Serializable

@Serializable
data class ModelPolicy(val thinking: ThinkingMode = ThinkingMode.DEFAULT, val reasoningTokens: Int = 0)
