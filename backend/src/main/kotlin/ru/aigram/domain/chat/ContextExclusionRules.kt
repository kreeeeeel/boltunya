package ru.aigram.domain.chat

import kotlinx.serialization.Serializable

@Serializable
data class ContextExclusionRules(val entries: List<ContextExclusionRule> = emptyList(), val revision: Long = 0) {
    fun excludes(messageId: String): Boolean {
        val id = messageId.toLongOrNull()

        if (id == null) {
            return false
        }

        if (id <= 0) {
            return false
        }
        return entries.asReversed().firstOrNull { entry ->
            when (entry.scope) {
                ContextExclusionScope.MESSAGE -> id == entry.messageId
                ContextExclusionScope.THROUGH -> id <= entry.messageId
                ContextExclusionScope.FROM -> id >= entry.messageId
            }
        }?.excluded ?: false
    }
}
