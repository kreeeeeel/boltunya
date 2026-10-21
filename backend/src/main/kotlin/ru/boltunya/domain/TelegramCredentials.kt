package ru.boltunya.domain

import jakarta.validation.constraints.Positive
import kotlinx.serialization.Serializable
import ru.boltunya.validation.InputFormat
import ru.boltunya.validation.InputRule

@Serializable
data class TelegramCredentials(
    @field:Positive
    val apiId: Int,
    @field:InputFormat(InputRule.CREDENTIALS_HASH)
    val apiHash: String,
    @field:InputFormat(InputRule.CREDENTIALS_PHONE)
    val phone: String,
)
