package ru.aigram.automation.proactive

import ru.aigram.domain.ChatMessage
import ru.aigram.domain.ChatRule
import ru.aigram.domain.ReplyResult

class ProactiveTask(
    val allowed: () -> Boolean,
    val generate: (ChatRule, List<ChatMessage>) -> ReplyResult,
    val prepareDelivery: () -> Boolean,
    val trigger: String,
)
