package ru.aigram.domain.media

import ru.aigram.domain.chat.MessageEntity
import java.nio.file.Path

data class OutgoingMedia(
    val path: Path,
    val kind: OutgoingMediaKind,
    val caption: String = "",
    val captionEntities: List<MessageEntity> = emptyList(),
)
