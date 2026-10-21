package ru.aigram.domain.ai

import kotlinx.serialization.Serializable
import ru.aigram.domain.chat.MessageEntity
import ru.aigram.domain.speech.GeneratedVoice

@Serializable
data class ReplyResult(
    val text: String,
    val durationMs: Long,
    val tokensPerSecond: Double,
    val model: String = "",
    val stickerId: String = "",
    val entities: List<MessageEntity> = emptyList(),
    val voice: GeneratedVoice? = null,
)
