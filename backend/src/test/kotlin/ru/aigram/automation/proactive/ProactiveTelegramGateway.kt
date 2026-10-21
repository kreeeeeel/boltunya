package ru.aigram.automation.proactive

import ru.aigram.automation.FakeTelegramGateway
import ru.aigram.domain.ChatSummary
import ru.aigram.domain.TelegramStatus
import ru.aigram.telegram.TelegramGateway

class ProactiveTelegramGateway(val delegate: FakeTelegramGateway = FakeTelegramGateway()) :
    TelegramGateway by delegate {
    var owner = "100"
    var summaries: List<ChatSummary> = emptyList()
    override fun status(): TelegramStatus = TelegramStatus(state = "ready", userId = owner)
    override fun chats(): List<ChatSummary> = summaries
}
