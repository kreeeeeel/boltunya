package ru.aigram.http.endpoint

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import org.springframework.stereotype.Component
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.TelegramCredentials
import ru.aigram.http.InputValidation
import ru.aigram.http.RequestReader
import ru.aigram.storage.WritingStyleStore
import ru.aigram.telegram.TelegramGateway

private const val MAX_AUTH_INPUT_CHARACTERS = 256

@Component
class AuthenticationEndpoint @Inject constructor(
    private val json: Json,
    private val telegram: TelegramGateway,
    private val replies: ReplyCoordinator,
    private val writing: WritingStyleStore,
    private val validation: InputValidation,
    private val input: RequestReader,
) {
    fun connect(body: JsonObject) {
        val credentials = json.decodeFromJsonElement<TelegramCredentials>(body)
        validation.validate(credentials)
        telegram.connect(credentials)
    }
    fun submit(body: JsonObject) {
        telegram.submit(input.text(body, "value", MAX_AUTH_INPUT_CHARACTERS))
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
