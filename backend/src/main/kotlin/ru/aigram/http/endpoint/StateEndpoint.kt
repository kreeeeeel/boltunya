package ru.aigram.http.endpoint

import jakarta.inject.Inject
import ru.aigram.ai.EngineManager
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ApiState
import ru.aigram.storage.SettingsStore
import ru.aigram.telegram.TelegramGateway

class StateEndpoint @Inject constructor(
    private val settings: SettingsStore,
    private val telegram: TelegramGateway,
    private val engine: EngineManager,
    private val replies: ReplyCoordinator,
) {
    fun get(): ApiState = ApiState(
        telegram.status(),
        engine.status(),
        settings.get(),
        telegram.chats(),
        replies.currentActivities(),
        replies.queue(),
    )
}
