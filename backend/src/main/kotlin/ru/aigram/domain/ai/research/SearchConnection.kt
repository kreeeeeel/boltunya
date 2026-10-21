package ru.aigram.domain.ai.research

import kotlinx.serialization.Serializable

@Serializable
data class SearchConnection(val configured: Boolean, val state: SearchConnectionState)
