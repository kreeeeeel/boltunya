package ru.aigram.transformer.system

import ru.aigram.contract.dto.ReplyQueue
import ru.aigram.contract.dto.State
import ru.aigram.domain.system.ApiState
import ru.aigram.transformer.activity.toContract
import ru.aigram.transformer.chat.toContract
import ru.aigram.transformer.settings.toContract
import ru.aigram.transformer.telegram.toContract

fun ApiState.toContract(): State = State(
    telegram.toContract(),
    engine.toContract(),
    settings.toContract(),
    chats.stream().map { it.toContract() }.toList(),
    activity.stream().map { it.toContract() }.toList(),
    ReplyQueue(
        replyQueue.paused,
        replyQueue.queued.toLong(),
        replyQueue.running.toLong(),
        replyQueue.maxParallelTasks,
        replyQueue.activeTasks,
    ),
)
