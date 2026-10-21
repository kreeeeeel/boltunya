package ru.aigram.transformer.chat

import ru.aigram.domain.chat.MessageDeletionOptions
import ru.aigram.domain.chat.MessageDeletionResult
import ru.aigram.contract.dto.MessageDeletionOptions as ContractMessageDeletionOptions
import ru.aigram.contract.dto.MessageDeletionResult as ContractMessageDeletionResult

fun MessageDeletionOptions.toContract(): ContractMessageDeletionOptions = ContractMessageDeletionOptions(
    canDeleteForSelf = canDeleteForSelf,
    canDeleteForEveryone = canDeleteForEveryone,
)

fun MessageDeletionResult.toContract(): ContractMessageDeletionResult = ContractMessageDeletionResult(
    chatId = chatId,
    messageId = messageId,
    deletedAt = deletedAt,
)
