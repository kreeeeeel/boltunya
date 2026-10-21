package ru.aigram.transformer.chat

import org.springframework.stereotype.Component
import ru.aigram.domain.chat.MessageDeletionOptions
import ru.aigram.domain.chat.MessageDeletionResult
import ru.aigram.contract.dto.MessageDeletionOptions as ContractMessageDeletionOptions
import ru.aigram.contract.dto.MessageDeletionResult as ContractMessageDeletionResult

@Component
class MessageDeletionTransformer {
    fun toContract(value: MessageDeletionOptions): ContractMessageDeletionOptions = ContractMessageDeletionOptions(
        canDeleteForSelf = value.canDeleteForSelf,
        canDeleteForEveryone = value.canDeleteForEveryone,
    )

    fun toContract(value: MessageDeletionResult): ContractMessageDeletionResult = ContractMessageDeletionResult(
        chatId = value.chatId,
        messageId = value.messageId,
        deletedAt = value.deletedAt,
    )
}
