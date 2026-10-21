package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.springframework.stereotype.Component
import ru.aigram.domain.chat.MessageDeletionOptions
import ru.aigram.domain.chat.MessageDeletionScope
import ru.aigram.exception.impl.InvalidHistoryIdentifierException
import ru.aigram.exception.impl.MessageDeletionNotAllowedException

@Component
class TelegramMessageDeletionPolicy {
    fun propertiesRequest(chatId: Long, messageId: String): TdApi.GetMessageProperties {
        val id = messageId.toLongOrNull()
        if (id == null || id <= 0 || id.toString() != messageId) {
            throw InvalidHistoryIdentifierException()
        }
        return TdApi.GetMessageProperties(chatId, id)
    }

    fun options(properties: TdApi.MessageProperties): MessageDeletionOptions = MessageDeletionOptions(
        canDeleteForSelf = properties.canBeDeletedOnlyForSelf,
        canDeleteForEveryone = properties.canBeDeletedForAllUsers,
    )

    fun deletionRequest(
        chatId: Long,
        messageId: String,
        scope: MessageDeletionScope,
        options: MessageDeletionOptions,
    ): TdApi.DeleteMessages {
        val message = propertiesRequest(chatId, messageId)
        val allowed = when (scope) {
            MessageDeletionScope.SELF -> options.canDeleteForSelf
            MessageDeletionScope.EVERYONE -> options.canDeleteForEveryone
        }
        if (!allowed) {
            throw MessageDeletionNotAllowedException()
        }
        return TdApi.DeleteMessages(chatId, longArrayOf(message.messageId), scope == MessageDeletionScope.EVERYONE)
    }
}
