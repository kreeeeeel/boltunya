package ru.aigram.domain.chat

import kotlinx.serialization.Serializable
import ru.aigram.domain.media.MediaAttachment

@Serializable
data class ChatMessage(
    val id: String,
    val chatId: String,
    val text: String,
    val outgoing: Boolean,
    val date: Long,
    val kind: String = "text",
    val media: MediaAttachment? = null,
    val entities: List<MessageEntity> = emptyList(),
    val read: Boolean = false,
    val automated: Boolean = false,
    val group: Boolean = false,
    val mentionsMe: Boolean = false,
    val topicId: Int = 0,
    val senderName: String = "",
    val senderAvatarUrl: String = "",
    val replyToId: String = "",
    val replyText: String = "",
    val senderId: String = "",
    val deleted: Boolean = false,
    val deletedAt: Long = 0,
    val contextExcluded: Boolean = false,
    val senderUsername: String = "",
    val mediaAlbumId: String = "",
)
