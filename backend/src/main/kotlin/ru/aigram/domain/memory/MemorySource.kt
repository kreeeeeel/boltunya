package ru.aigram.domain.memory

import kotlinx.serialization.Serializable

@Serializable
data class MemorySource(
    val id: String,
    val date: Long,
    val speaker: MemorySpeaker,
    val text: String,
    val kind: String,
    val sender: String,
    val senderId: String = "",
    val senderUsername: String = "",
)
