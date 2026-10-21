package ru.boltunya.domain

import jakarta.validation.constraints.Size
import kotlinx.serialization.Serializable
import ru.boltunya.validation.InputFormat
import ru.boltunya.validation.InputRule

@Serializable
data class WritingStyle(
    @field:InputFormat(InputRule.STYLE_ID)
    val id: String,
    @field:Size(min = 1, max = 40)
    val name: String,
    @field:Size(min = 1, max = 16)
    val emoji: String,
    @field:Size(min = 1, max = 4000)
    val instructions: String,
)
