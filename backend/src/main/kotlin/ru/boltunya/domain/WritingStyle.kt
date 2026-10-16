package ru.boltunya.domain

import jakarta.validation.constraints.Size
import kotlinx.serialization.Serializable
import ru.boltunya.validation.ResourcePattern

@Serializable
data class WritingStyle(
    @field:ResourcePattern("common/style-id")
    val id: String,
    @field:Size(min = 1, max = 40)
    val name: String,
    @field:Size(min = 1, max = 16)
    val emoji: String,
    @field:Size(min = 1, max = 4000)
    val instructions: String,
)
