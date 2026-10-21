package ru.aigram.domain.memory

import kotlinx.serialization.Serializable

@Serializable
internal data class ParticipantRecord(
    val id: String,
    val name: String,
    val username: String,
    val lastSeenDate: Long,
    val lastMessageId: Long,
)
