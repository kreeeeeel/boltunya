package ru.aigram.automation.proactive

import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatRule

class ProactiveTask(
    val allowed: () -> Boolean,
    val generate: (ChatRule, List<ChatMessage>) -> ReplyResult,
    val prepareDelivery: () -> Boolean,
    val trigger: String,
)
