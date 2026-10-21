package ru.aigram.domain.media

import jakarta.validation.constraints.Size
import kotlinx.serialization.Serializable

@Serializable
data class SendMediaRequest(
    @field:Size(min = 1, max = 10)
    val uploadIds: List<String>,
    val kind: OutgoingMediaKind,
    @field:Size(max = 1024)
    val caption: String = "",
)
