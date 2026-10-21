package ru.boltunya.storage

import kotlinx.serialization.Serializable
import ru.boltunya.domain.ContextExclusionScope

@Serializable
data class ContextExclusionRule(val messageId: Long, val scope: ContextExclusionScope, val excluded: Boolean)
