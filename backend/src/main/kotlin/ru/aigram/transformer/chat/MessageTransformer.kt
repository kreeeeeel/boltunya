package ru.aigram.transformer.chat

import org.springframework.stereotype.Component
import ru.aigram.contract.dto.Message
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.MessageEntity
import ru.aigram.transformer.media.MediaTransformer
import ru.aigram.contract.dto.MessageEntity as ContractMessageEntity

@Component
class MessageTransformer(private val media: MediaTransformer) {
    fun toContract(value: ChatMessage): Message = Message(
        id = value.id,
        chatId = value.chatId,
        text = value.text,
        outgoing = value.outgoing,
        date = value.date,
        kind = value.kind,
        media = value.media?.let(media::toContract),
        entities = value.entities.stream().map(::toContract).toList(),
        read = value.read,
        automated = value.automated,
        group = value.group,
        mentionsMe = value.mentionsMe,
        topicId = value.topicId,
        senderName = value.senderName,
        senderAvatarUrl = value.senderAvatarUrl,
        replyToId = value.replyToId,
        replyText = value.replyText,
        senderId = value.senderId,
        deleted = value.deleted,
        deletedAt = value.deletedAt,
        contextExcluded = value.contextExcluded,
        senderUsername = value.senderUsername,
        mediaAlbumId = value.mediaAlbumId,
    )

    private fun toContract(value: MessageEntity): ContractMessageEntity = ContractMessageEntity(
        offset = value.offset,
        length = value.length,
        type = value.type,
        url = value.url,
        language = value.language,
    )
}
