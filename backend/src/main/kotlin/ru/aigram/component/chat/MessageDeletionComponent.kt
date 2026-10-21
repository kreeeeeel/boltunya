package ru.aigram.component.chat

import org.springframework.stereotype.Component
import ru.aigram.domain.chat.MessageDeletionOptions
import ru.aigram.domain.chat.MessageDeletionResult
import ru.aigram.domain.chat.MessageDeletionScope
import ru.aigram.exception.impl.ChatNotFoundException
import ru.aigram.exception.impl.InvalidHistoryIdentifierException
import ru.aigram.exception.impl.TelegramNotConnectedException
import ru.aigram.telegram.TelegramGateway
import ru.aigram.telegram.TelegramMessageDeletionPolicy

@Component
class MessageDeletionComponent(
    private val telegram: TelegramGateway,
    private val policy: TelegramMessageDeletionPolicy,
) {
    fun options(chatId: String, messageId: String): MessageDeletionOptions {
        validate(chatId, messageId)
        return telegram.messageDeletionOptions(chatId, messageId)
    }

    fun delete(chatId: String, messageId: String, scope: MessageDeletionScope): MessageDeletionResult {
        val permissions = options(chatId, messageId)
        policy.deletionRequest(chatId.toLong(), messageId, scope, permissions)
        return telegram.deleteMessage(chatId, messageId, scope)
    }

    private fun validate(chatId: String, messageId: String) {
        val id = chatId.toLongOrNull()
        if (id == null || id == 0L || id.toString() != chatId) {
            throw InvalidHistoryIdentifierException()
        }
        val status = telegram.status()
        if (status.state != "ready" || status.userId.isEmpty()) {
            throw TelegramNotConnectedException()
        }
        if (telegram.chats().stream().noneMatch { it.id == chatId }) {
            throw ChatNotFoundException()
        }
        policy.propertiesRequest(id, messageId)
    }
}
