package ru.boltunya.domain

import kotlinx.serialization.Serializable

@Serializable
data class ApiState(
    val telegram: TelegramStatus,
    val engine: EngineStatus,
    val settings: AppSettings,
    val chats: List<ChatSummary>,
    val activity: List<ActivityEntry>,
    val replyQueue: ReplyQueueState = ReplyQueueState(),
)
