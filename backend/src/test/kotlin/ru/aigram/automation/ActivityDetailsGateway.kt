package ru.aigram.automation

import ru.aigram.domain.chat.ChatSummary
import ru.aigram.domain.telegram.TelegramStatus
import ru.aigram.telegram.TelegramGateway

class ActivityDetailsGateway(val delegate: FakeTelegramGateway = FakeTelegramGateway()) : TelegramGateway by delegate {
    @Volatile var owner = "100"

    @Volatile var beforeChats: () -> Unit = {}

    override fun status(): TelegramStatus = TelegramStatus(state = "ready", userId = owner)

    override fun chats(): List<ChatSummary> {
        beforeChats()
        return delegate.chats()
    }
}
