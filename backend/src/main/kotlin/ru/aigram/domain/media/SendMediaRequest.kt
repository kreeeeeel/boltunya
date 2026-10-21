package ru.aigram.domain.media

import jakarta.validation.constraints.Size
import kotlinx.serialization.Serializable
import ru.aigram.domain.chat.MessageEntity

@Serializable
data class SendMediaRequest(
    @field:Size(min = 1, max = 10)
    val uploadIds: List<String>,
    val kind: OutgoingMediaKind,
    @field:Size(max = 1024)
    val caption: String = "",
    @field:Size(max = 100)
    val captionEntities: List<MessageEntity> = emptyList(),
)
