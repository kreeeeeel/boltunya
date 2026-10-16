package ru.boltunya.domain

import jakarta.validation.constraints.Positive
import kotlinx.serialization.Serializable
import ru.boltunya.validation.ResourcePattern

@Serializable
data class TelegramCredentials(
    @field:Positive
    val apiId: Int,
    @field:ResourcePattern("common/credentials-hash")
    val apiHash: String,
    @field:ResourcePattern("common/credentials-phone")
    val phone: String,
)
