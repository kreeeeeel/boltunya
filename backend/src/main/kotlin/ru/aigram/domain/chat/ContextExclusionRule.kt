package ru.aigram.domain.chat

import kotlinx.serialization.Serializable

@Serializable
data class ContextExclusionRule(val messageId: Long, val scope: ContextExclusionScope, val excluded: Boolean)
