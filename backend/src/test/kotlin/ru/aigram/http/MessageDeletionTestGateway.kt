package ru.aigram.http

import ru.aigram.domain.MessageDeletionOptions
import ru.aigram.domain.MessageDeletionResult
import ru.aigram.domain.MessageDeletionScope
import ru.aigram.exception.impl.TelegramDeleteFailedException
import ru.aigram.memory.MemoryTelegramGateway
import ru.aigram.telegram.TelegramGateway

class MessageDeletionTestGateway(val delegate: MemoryTelegramGateway = MemoryTelegramGateway()) :
    TelegramGateway by delegate {
    var permissions = MessageDeletionOptions(canDeleteForSelf = true, canDeleteForEveryone = false)
    var failure = false
    val deletions = mutableListOf<Triple<String, String, MessageDeletionScope>>()
    override fun messageDeletionOptions(chatId: String, messageId: String): MessageDeletionOptions = permissions
    override fun deleteMessage(chatId: String, messageId: String, scope: MessageDeletionScope): MessageDeletionResult {
        if (failure) throw TelegramDeleteFailedException()
        deletions.add(Triple(chatId, messageId, scope))
        return MessageDeletionResult(chatId, messageId, 100)
    }
}
