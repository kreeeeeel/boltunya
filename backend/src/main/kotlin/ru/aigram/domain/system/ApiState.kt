package ru.aigram.domain.system

import kotlinx.serialization.Serializable
import ru.aigram.domain.ai.EngineStatus
import ru.aigram.domain.chat.ChatSummary
import ru.aigram.domain.events.ActivityEntry
import ru.aigram.domain.events.ReplyQueueState
import ru.aigram.domain.settings.AppSettings
import ru.aigram.domain.telegram.TelegramStatus

@Serializable
data class ApiState(
    val telegram: TelegramStatus,
    val engine: EngineStatus,
    val settings: AppSettings,
    val chats: List<ChatSummary>,
    val activity: List<ActivityEntry>,
    val replyQueue: ReplyQueueState = ReplyQueueState(),
)
