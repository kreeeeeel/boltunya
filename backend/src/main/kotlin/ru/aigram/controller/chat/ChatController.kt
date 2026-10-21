package ru.aigram.controller.chat

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import ru.aigram.component.chat.ChatCommunicationComponent
import ru.aigram.component.chat.ChatHistoryComponent
import ru.aigram.component.chat.ContextExclusionComponent
import ru.aigram.component.chat.MessageDeletionComponent
import ru.aigram.component.chat.MessageEditingComponent
import ru.aigram.component.media.MediaSendingComponent
import ru.aigram.contract.api.ChatsApi
import ru.aigram.contract.dto.ChatGallery
import ru.aigram.contract.dto.ChatRuleInput
import ru.aigram.contract.dto.ContextExclusionUpdate
import ru.aigram.contract.dto.Message
import ru.aigram.contract.dto.MessageDeletionOptions
import ru.aigram.contract.dto.MessageDeletionRequest
import ru.aigram.contract.dto.MessageDeletionResult
import ru.aigram.contract.dto.MessageEditOptions
import ru.aigram.contract.dto.MessageEditRequest
import ru.aigram.contract.dto.MessageLink
import ru.aigram.contract.dto.Ok
import ru.aigram.contract.dto.SendMediaRequest
import ru.aigram.contract.dto.SendStickerRequest
import ru.aigram.contract.dto.Text
import ru.aigram.domain.chat.MessageDeletionScope
import ru.aigram.transformer.chat.toContract
import ru.aigram.transformer.chat.toDomain
import ru.aigram.transformer.media.toDomain as toMediaDomain

@RestController
class ChatController(
    private val history: ChatHistoryComponent,
    private val communication: ChatCommunicationComponent,
    private val exclusions: ContextExclusionComponent,
    private val deletion: MessageDeletionComponent,
    private val media: MediaSendingComponent,
    private val editing: MessageEditingComponent,
) : ChatsApi {
    override fun messageEditOptions(chatId: String, messageId: String): ResponseEntity<MessageEditOptions> =
        ResponseEntity.ok(editing.options(chatId, messageId).toContract())

    override fun editMessage(
        chatId: String,
        messageId: String,
        messageEditRequest: MessageEditRequest,
    ): ResponseEntity<Message> = ResponseEntity.ok(
        editing.edit(chatId, messageId, messageEditRequest.toDomain()).toContract(),
    )

    override fun chatGallery(chatId: String, around: String): ResponseEntity<ChatGallery> =
        ResponseEntity.ok(history.gallery(chatId, around).toContract())

    override fun deleteMessage(
        chatId: String,
        messageId: String,
        messageDeletionRequest: MessageDeletionRequest,
    ): ResponseEntity<MessageDeletionResult> = ResponseEntity.ok(
        deletion.delete(
            chatId,
            messageId,
            MessageDeletionScope.valueOf(messageDeletionRequest.scope.name),
        ).toContract(),
    )

    override fun excludeMessageContext(
        chatId: String,
        messageId: String,
        contextExclusionUpdate: ContextExclusionUpdate,
    ): ResponseEntity<Ok> {
        exclusions.update(chatId, messageId, contextExclusionUpdate.toDomain())
        return ResponseEntity.ok(Ok(true))
    }

    override fun getDeletedMessages(chatId: String, before: String?): ResponseEntity<List<Message>> =
        ResponseEntity.ok(history.deleted(chatId, before).stream().map { it.toContract() }.toList())

    override fun history(chatId: String, before: String?): ResponseEntity<List<Message>> =
        ResponseEntity.ok(history.history(chatId, before).stream().map { it.toContract() }.toList())

    override fun messageDeletionOptions(chatId: String, messageId: String): ResponseEntity<MessageDeletionOptions> =
        ResponseEntity.ok(deletion.options(chatId, messageId).toContract())

    override fun messageLink(chatId: String, messageId: String): ResponseEntity<MessageLink> =
        ResponseEntity.ok(MessageLink(history.link(chatId, messageId).url))

    override fun restoreChatContext(chatId: String): ResponseEntity<Ok> {
        exclusions.clear(chatId)
        return ResponseEntity.ok(Ok(true))
    }

    override fun rule(chatId: String, chatRule: ChatRuleInput): ResponseEntity<Ok> {
        communication.saveRule(chatId, chatRule.toDomain())
        return ResponseEntity.ok(Ok(true))
    }

    override fun send(chatId: String, text: Text): ResponseEntity<Ok> {
        communication.send(chatId, text.text)
        return ResponseEntity.ok(Ok(true))
    }

    override fun sendAttachments(chatId: String, sendMediaRequest: SendMediaRequest): ResponseEntity<Ok> {
        media.send(chatId, sendMediaRequest.toMediaDomain())
        return ResponseEntity.ok(Ok(true))
    }

    override fun sendSticker(chatId: String, sendStickerRequest: SendStickerRequest): ResponseEntity<Ok> {
        media.sticker(chatId, sendStickerRequest.stickerId)
        return ResponseEntity.ok(Ok(true))
    }
}
