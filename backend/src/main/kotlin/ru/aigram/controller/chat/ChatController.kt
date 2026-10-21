package ru.aigram.controller.chat

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import ru.aigram.component.chat.ChatCommunicationComponent
import ru.aigram.component.chat.ChatHistoryComponent
import ru.aigram.component.chat.ContextExclusionComponent
import ru.aigram.component.chat.MessageDeletionComponent
import ru.aigram.component.media.MediaSendingComponent
import ru.aigram.contract.api.ChatsApi
import ru.aigram.contract.dto.ChatGallery
import ru.aigram.contract.dto.ChatRuleInput
import ru.aigram.contract.dto.ContextExclusionUpdate
import ru.aigram.contract.dto.Message
import ru.aigram.contract.dto.MessageDeletionOptions
import ru.aigram.contract.dto.MessageDeletionRequest
import ru.aigram.contract.dto.MessageDeletionResult
import ru.aigram.contract.dto.MessageLink
import ru.aigram.contract.dto.Ok
import ru.aigram.contract.dto.SendMediaRequest
import ru.aigram.contract.dto.SendStickerRequest
import ru.aigram.contract.dto.Text
import ru.aigram.domain.chat.MessageDeletionScope
import ru.aigram.transformer.chat.ChatRuleTransformer
import ru.aigram.transformer.chat.ChatTransformer
import ru.aigram.transformer.chat.ContextExclusionTransformer
import ru.aigram.transformer.chat.MessageDeletionTransformer
import ru.aigram.transformer.chat.MessageTransformer
import ru.aigram.transformer.media.MediaTransformer

@RestController
class ChatController(
    private val history: ChatHistoryComponent,
    private val communication: ChatCommunicationComponent,
    private val exclusions: ContextExclusionComponent,
    private val deletion: MessageDeletionComponent,
    private val media: MediaSendingComponent,
    private val chats: ChatTransformer,
    private val messages: MessageTransformer,
    private val rules: ChatRuleTransformer,
    private val context: ContextExclusionTransformer,
    private val deleted: MessageDeletionTransformer,
    private val attachments: MediaTransformer,
) : ChatsApi {
    override fun chatGallery(chatId: String, around: String): ResponseEntity<ChatGallery> =
        ResponseEntity.ok(chats.toContract(history.gallery(chatId, around)))

    override fun deleteMessage(
        chatId: String,
        messageId: String,
        messageDeletionRequest: MessageDeletionRequest,
    ): ResponseEntity<MessageDeletionResult> = ResponseEntity.ok(
        deleted.toContract(
            deletion.delete(chatId, messageId, MessageDeletionScope.valueOf(messageDeletionRequest.scope.name)),
        ),
    )

    override fun excludeMessageContext(
        chatId: String,
        messageId: String,
        contextExclusionUpdate: ContextExclusionUpdate,
    ): ResponseEntity<Ok> {
        exclusions.update(chatId, messageId, context.toDomain(contextExclusionUpdate))
        return ResponseEntity.ok(Ok(true))
    }

    override fun getDeletedMessages(chatId: String, before: String?): ResponseEntity<List<Message>> =
        ResponseEntity.ok(history.deleted(chatId, before).stream().map(messages::toContract).toList())

    override fun history(chatId: String, before: String?): ResponseEntity<List<Message>> =
        ResponseEntity.ok(history.history(chatId, before).stream().map(messages::toContract).toList())

    override fun messageDeletionOptions(chatId: String, messageId: String): ResponseEntity<MessageDeletionOptions> =
        ResponseEntity.ok(deleted.toContract(deletion.options(chatId, messageId)))

    override fun messageLink(chatId: String, messageId: String): ResponseEntity<MessageLink> =
        ResponseEntity.ok(MessageLink(history.link(chatId, messageId).url))

    override fun restoreChatContext(chatId: String): ResponseEntity<Ok> {
        exclusions.clear(chatId)
        return ResponseEntity.ok(Ok(true))
    }

    override fun rule(chatId: String, chatRule: ChatRuleInput): ResponseEntity<Ok> {
        communication.saveRule(chatId, rules.toDomain(chatRule))
        return ResponseEntity.ok(Ok(true))
    }

    override fun send(chatId: String, text: Text): ResponseEntity<Ok> {
        communication.send(chatId, text.text)
        return ResponseEntity.ok(Ok(true))
    }

    override fun sendAttachments(chatId: String, sendMediaRequest: SendMediaRequest): ResponseEntity<Ok> {
        media.send(chatId, attachments.toDomain(sendMediaRequest))
        return ResponseEntity.ok(Ok(true))
    }

    override fun sendSticker(chatId: String, sendStickerRequest: SendStickerRequest): ResponseEntity<Ok> {
        media.sticker(chatId, sendStickerRequest.stickerId)
        return ResponseEntity.ok(Ok(true))
    }
}
