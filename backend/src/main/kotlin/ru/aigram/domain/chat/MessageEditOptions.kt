package ru.aigram.domain.chat

data class MessageEditOptions(
    val text: String,
    val entities: List<MessageEditEntity>,
    val revision: String,
    val kind: MessageEditKind,
    val maxLength: Int,
)
