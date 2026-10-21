package ru.aigram.memory

import kotlinx.serialization.Serializable

@Serializable
data class ParticipantIdentity(val id: String, val name: String, val username: String)
