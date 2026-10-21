package ru.aigram.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MemoryCollectionScope {
    @SerialName("ai-enabled")
    AI_ENABLED,

    @SerialName("all-allowed")
    ALL_ALLOWED,
}
