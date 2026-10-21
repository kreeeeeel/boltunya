package ru.aigram.transformer.chat

import ru.aigram.contract.dto.Message
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.MessageEntity
import ru.aigram.contract.dto.MessageEntity as ContractMessageEntity
import ru.aigram.transformer.media.toContract as toMediaContract

fun ChatMessage.toContract(): Message = Message(
    id = id,
    chatId = chatId,
    text = text,
    outgoing = outgoing,
    date = date,
    kind = kind,
    media = media?.toMediaContract(),
    entities = entities.stream().map { it.toContract() }.toList(),
    read = read,
    automated = automated,
    group = group,
    mentionsMe = mentionsMe,
    topicId = topicId,
    senderName = senderName,
    senderAvatarUrl = senderAvatarUrl,
    replyToId = replyToId,
    replyText = replyText,
    senderId = senderId,
    deleted = deleted,
    deletedAt = deletedAt,
    contextExcluded = contextExcluded,
    senderUsername = senderUsername,
    mediaAlbumId = mediaAlbumId,
    editedAt = editedAt,
)

private fun MessageEntity.toContract(): ContractMessageEntity = ContractMessageEntity(
    offset = offset,
    length = length,
    type = type,
    url = url,
    language = language,
)
