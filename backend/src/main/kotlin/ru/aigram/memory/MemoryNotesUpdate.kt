package ru.aigram.memory

import jakarta.validation.constraints.Size
import kotlinx.serialization.Serializable

@Serializable
data class MemoryNotesUpdate(@field:Size(max = 16000) val notes: String, @field:Size(max = 64) val revision: String)
