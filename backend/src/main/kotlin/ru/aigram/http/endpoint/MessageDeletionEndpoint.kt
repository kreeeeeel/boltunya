package ru.aigram.http.endpoint

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import org.springframework.stereotype.Component
import ru.aigram.domain.MessageDeletionOptions
import ru.aigram.domain.MessageDeletionRequest
import ru.aigram.domain.MessageDeletionResult
import ru.aigram.exception.impl.ChatNotFoundException
import ru.aigram.exception.impl.InvalidHistoryIdentifierException
import ru.aigram.exception.impl.TelegramNotConnectedException
import ru.aigram.telegram.TelegramGateway
import ru.aigram.telegram.TelegramMessageDeletionPolicy

@Component
class MessageDeletionEndpoint @Inject constructor(
    private val json: Json,
    private val telegram: TelegramGateway,
    private val policy: TelegramMessageDeletionPolicy,
) {
    fun options(chatId: String, messageId: String): MessageDeletionOptions {
        validate(chatId, messageId)
        return telegram.messageDeletionOptions(chatId, messageId)
    }

    fun delete(chatId: String, messageId: String, body: JsonObject): MessageDeletionResult {
        val request = json.decodeFromJsonElement<MessageDeletionRequest>(body)
        val permissions = options(chatId, messageId)
        policy.deletionRequest(chatId.toLong(), messageId, request.scope, permissions)
        return telegram.deleteMessage(chatId, messageId, request.scope)
    }

    private fun validate(chatId: String, messageId: String) {
        val id = chatId.toLongOrNull()
        if (id == null || id == 0L || id.toString() != chatId) throw InvalidHistoryIdentifierException()
        val status = telegram.status()
        if (status.state != "ready" || status.userId.isEmpty()) throw TelegramNotConnectedException()
        if (telegram.chats().stream().noneMatch { it.id == chatId }) throw ChatNotFoundException()
        policy.propertiesRequest(id, messageId)
    }
}
