package ru.aigram.domain.memory

import kotlinx.serialization.Serializable

@Serializable
data class MemoryRefreshRequest(val rebuild: Boolean = false, val immediate: Boolean = false)
