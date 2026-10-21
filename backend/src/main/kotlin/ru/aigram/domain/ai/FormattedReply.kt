package ru.aigram.domain.ai

import ru.aigram.domain.chat.MessageEntity

data class FormattedReply(val text: String, val entities: List<MessageEntity> = emptyList())
