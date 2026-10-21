package ru.aigram.storage

import kotlinx.serialization.Serializable
import ru.aigram.domain.ContextExclusionScope

@Serializable
data class ContextExclusionRule(val messageId: Long, val scope: ContextExclusionScope, val excluded: Boolean)
