package ru.aigram.domain.chat.history

import ru.aigram.domain.chat.ChatMessage
import java.nio.file.attribute.FileTime

internal data class ArchiveBucketSnapshot(
    val sizeBytes: Long,
    val modifiedAt: FileTime,
    val fileKey: Any?,
    val messages: Map<String, ChatMessage>,
)
