package ru.boltunya.memory

import kotlinx.serialization.Serializable

@Serializable
data class ParticipantIdentity(val id: String, val name: String, val username: String)
