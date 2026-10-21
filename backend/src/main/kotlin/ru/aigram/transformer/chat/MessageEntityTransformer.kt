package ru.aigram.transformer.chat

import ru.aigram.domain.chat.MessageEntity
import ru.aigram.contract.dto.MessageEntity as ContractMessageEntity

fun MessageEntity.toContract(): ContractMessageEntity = ContractMessageEntity(
    offset = offset,
    length = length,
    type = type,
    url = url,
    language = language,
)

fun ContractMessageEntity.toDomain(): MessageEntity = MessageEntity(
    offset = offset,
    length = length,
    type = type,
    url = url.orEmpty(),
    language = language.orEmpty(),
)
