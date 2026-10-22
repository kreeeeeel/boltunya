package ru.aigram.domain.chat

import kotlinx.serialization.Serializable

@Serializable
data class ChatSummary(
    val id: String,
    val title: String,
    val preview: String,
    val date: Long,
    val unread: Int,
    val rule: ChatRule,
    val avatarUrl: String = "",
    val kind: String = "private",
    val wallpaper: ChatWallpaper? = null,
    val darkWallpaper: ChatWallpaper? = null,
    val lastMessageId: String = "",
    val joined: Boolean? = null,
    val username: String = "",
    val memberCount: Int? = null,
    val lastMessageOutgoing: Boolean = false,
    val lastMessageSender: String = "",
    val typingUsers: List<ChatTypingUser> = emptyList(),
    val lastMessageRead: Boolean? = null,
)
