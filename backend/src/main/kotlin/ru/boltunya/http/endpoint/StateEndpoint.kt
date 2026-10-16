package ru.boltunya.http.endpoint

import jakarta.inject.Inject
import ru.boltunya.ai.EngineManager
import ru.boltunya.automation.ReplyCoordinator
import ru.boltunya.domain.ApiState
import ru.boltunya.storage.SettingsStore
import ru.boltunya.telegram.TelegramGateway

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
        replies.activities(),
        replies.queue(),
    )
}
