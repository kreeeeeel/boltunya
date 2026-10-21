package ru.boltunya.domain

import kotlinx.serialization.Serializable

@Serializable
data class ContextExclusionUpdate(
    val scope: ContextExclusionScope = ContextExclusionScope.MESSAGE,
    val excluded: Boolean = true,
)
