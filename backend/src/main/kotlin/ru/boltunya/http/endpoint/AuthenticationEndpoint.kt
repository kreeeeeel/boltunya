package ru.boltunya.http.endpoint

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import ru.boltunya.automation.ReplyCoordinator
import ru.boltunya.domain.TelegramCredentials
import ru.boltunya.http.InputValidation
import ru.boltunya.http.RequestReader
import ru.boltunya.storage.WritingStyleStore
import ru.boltunya.telegram.TelegramGateway

private const val MAX_AUTH_INPUT_CHARACTERS = 256

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
