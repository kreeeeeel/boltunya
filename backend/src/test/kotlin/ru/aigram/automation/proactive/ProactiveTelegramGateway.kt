package ru.aigram.automation.proactive

import ru.aigram.automation.FakeTelegramGateway
import ru.aigram.domain.chat.ChatSummary
import ru.aigram.domain.telegram.TelegramStatus
import ru.aigram.telegram.TelegramGateway

class ProactiveTelegramGateway(val delegate: FakeTelegramGateway = FakeTelegramGateway()) :
    TelegramGateway by delegate {
    @Volatile var owner = "100"
    var summaries: List<ChatSummary> = emptyList()
    override fun status(): TelegramStatus = TelegramStatus(state = "ready", userId = owner)
    override fun chats(): List<ChatSummary> = summaries
}
