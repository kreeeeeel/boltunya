package ru.boltunya.flows

import ru.boltunya.domain.ReplyResult

data class FlowExecution(
    val flowId: String,
    val chatId: String,
    val reply: ReplyResult,
    val values: Map<String, String>,
)
