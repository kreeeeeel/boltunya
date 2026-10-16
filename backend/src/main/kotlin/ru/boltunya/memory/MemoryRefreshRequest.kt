package ru.boltunya.memory

import kotlinx.serialization.Serializable

@Serializable
data class MemoryRefreshRequest(val rebuild: Boolean = false, val immediate: Boolean = false)
