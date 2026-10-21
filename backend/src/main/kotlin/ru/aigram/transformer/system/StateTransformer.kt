package ru.aigram.transformer.system

import org.springframework.stereotype.Component
import ru.aigram.contract.dto.ReplyQueue
import ru.aigram.contract.dto.State
import ru.aigram.domain.system.ApiState
import ru.aigram.transformer.activity.ActivityTransformer
import ru.aigram.transformer.chat.ChatTransformer
import ru.aigram.transformer.settings.SettingsTransformer
import ru.aigram.transformer.telegram.TelegramTransformer

@Component
class StateTransformer(
    private val telegram: TelegramTransformer,
    private val engine: EngineStateTransformer,
    private val settings: SettingsTransformer,
    private val chats: ChatTransformer,
    private val activity: ActivityTransformer,
) {
    fun toContract(value: ApiState): State = State(
        telegram.toContract(value.telegram),
        engine.toContract(value.engine),
        settings.toContract(value.settings),
        value.chats.stream().map(chats::toContract).toList(),
        value.activity.stream().map(activity::toContract).toList(),
        ReplyQueue(
            value.replyQueue.paused,
            value.replyQueue.queued.toLong(),
            value.replyQueue.running.toLong(),
            value.replyQueue.maxParallelTasks,
            value.replyQueue.activeTasks,
        ),
    )
}
