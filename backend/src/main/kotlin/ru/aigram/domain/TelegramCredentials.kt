package ru.aigram.domain

import jakarta.validation.constraints.Positive
import kotlinx.serialization.Serializable
import ru.aigram.validation.InputFormat
import ru.aigram.validation.InputRule

@Serializable
data class TelegramCredentials(
    @field:Positive
    val apiId: Int,
    @field:InputFormat(InputRule.CREDENTIALS_HASH)
    val apiHash: String,
    @field:InputFormat(InputRule.CREDENTIALS_PHONE)
    val phone: String,
)
