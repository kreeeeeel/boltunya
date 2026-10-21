package ru.boltunya.storage

import kotlinx.serialization.Serializable
import ru.boltunya.domain.ContextExclusionScope

@Serializable
data class ContextExclusionRules(val entries: List<ContextExclusionRule> = emptyList(), val revision: Long = 0) {
    fun excludes(messageId: String): Boolean {
        val id = messageId.toLongOrNull() ?: return false
        if (id <= 0) return false
        return entries.asReversed().firstOrNull { entry ->
            when (entry.scope) {
                ContextExclusionScope.MESSAGE -> id == entry.messageId
                ContextExclusionScope.THROUGH -> id <= entry.messageId
                ContextExclusionScope.FROM -> id >= entry.messageId
            }
        }?.excluded ?: false
    }
}
