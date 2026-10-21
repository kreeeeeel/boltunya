package ru.aigram.transformer.chat

import ru.aigram.domain.chat.MessageEditEntity
import ru.aigram.domain.chat.MessageEditEntityKind
import ru.aigram.domain.chat.MessageEditOptions
import ru.aigram.domain.chat.MessageEditRequest
import ru.aigram.contract.dto.MessageEditEntity as ContractMessageEditEntity
import ru.aigram.contract.dto.MessageEditEntityKind as ContractMessageEditEntityKind
import ru.aigram.contract.dto.MessageEditKind as ContractMessageEditKind
import ru.aigram.contract.dto.MessageEditOptions as ContractMessageEditOptions
import ru.aigram.contract.dto.MessageEditRequest as ContractMessageEditRequest

fun MessageEditOptions.toContract(): ContractMessageEditOptions = ContractMessageEditOptions(
    text,
    entities.stream().map { it.toContract() }.toList(),
    revision,
    ContractMessageEditKind.valueOf(kind.name),
    maxLength,
)

fun ContractMessageEditRequest.toDomain(): MessageEditRequest = MessageEditRequest(
    text,
    entities.stream().map { it.toDomain() }.toList(),
    revision,
)

private fun MessageEditEntity.toContract(): ContractMessageEditEntity = ContractMessageEditEntity(
    offset,
    length,
    ContractMessageEditEntityKind.valueOf(kind.name),
    sourceIndex,
    typeHint,
    url,
    language,
)

private fun ContractMessageEditEntity.toDomain(): MessageEditEntity = MessageEditEntity(
    offset,
    length,
    MessageEditEntityKind.valueOf(kind.name),
    sourceIndex,
    url = url,
    language = language,
)
