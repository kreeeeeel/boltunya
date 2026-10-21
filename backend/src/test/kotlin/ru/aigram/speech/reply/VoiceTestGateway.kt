package ru.aigram.speech.reply

import ru.aigram.automation.FakeTelegramGateway
import ru.aigram.domain.telegram.TelegramStatus
import ru.aigram.telegram.TelegramGateway

class VoiceTestGateway(val delegate: FakeTelegramGateway = FakeTelegramGateway()) : TelegramGateway by delegate {
    var owner = "100"
    override fun status(): TelegramStatus = TelegramStatus(state = "ready", userId = owner)
}
