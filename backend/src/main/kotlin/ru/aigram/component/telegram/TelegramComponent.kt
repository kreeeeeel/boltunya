package ru.aigram.component.telegram

import org.springframework.stereotype.Component
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.telegram.TelegramCredentials
import ru.aigram.exception.impl.InvalidFieldLengthException
import ru.aigram.storage.WritingStyleStore
import ru.aigram.telegram.TelegramGateway
import ru.aigram.validation.InputValidation

private const val MAX_AUTH_INPUT_CHARACTERS = 256

@Component
class TelegramComponent(
    private val telegram: TelegramGateway,
    private val replies: ReplyCoordinator,
    private val writing: WritingStyleStore,
    private val validation: InputValidation,
) {
    fun connect(credentials: TelegramCredentials) {
        validation.validate(credentials)
        telegram.connect(credentials)
    }

    fun submit(value: String) {
        val text = value.trim()
        if (text.isBlank() || text.length > MAX_AUTH_INPUT_CHARACTERS) {
            throw InvalidFieldLengthException("value", MAX_AUTH_INPUT_CHARACTERS)
        }
        telegram.submit(text)
    }

    fun restore() {
        telegram.restore()
    }

    fun logout() {
        replies.cancelPending()
        telegram.logout()
        writing.clear()
    }
}
