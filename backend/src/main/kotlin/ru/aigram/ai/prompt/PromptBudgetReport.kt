package ru.aigram.ai.prompt

import kotlinx.serialization.Serializable

@Serializable
data class PromptBudgetReport(
    val model: String,
    val profileId: String,
    val contextWindow: Int,
    val maxContextTokens: Int,
    val systemTokens: Int,
    val reservedOutputTokens: Int,
    val remainingTokens: Int,
    val fits: Boolean,
    val tokenEncoding: TokenEncoding,
    val preferences: ModelPromptSettings,
    val estimated: Boolean = true,
    val includesHistory: Boolean = false,
    val automaticContext: Boolean = false,
)
