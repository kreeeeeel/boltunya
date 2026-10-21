package ru.aigram.controller.integration

import it.tdlight.jni.TdApi
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.MessageEditOptions
import ru.aigram.domain.chat.MessageEditRequest
import ru.aigram.exception.impl.ChatNotFoundException
import ru.aigram.exception.impl.MessageEditConflictException
import ru.aigram.memory.MemoryTelegramGateway
import ru.aigram.telegram.TelegramEntityMapper
import ru.aigram.telegram.TelegramGateway
import ru.aigram.telegram.TelegramMessageEditEntities
import ru.aigram.telegram.TelegramMessageEditPolicy

class MessageEditingTestGateway(private val delegate: MemoryTelegramGateway = MemoryTelegramGateway()) :
    TelegramGateway by delegate {
    private val policy = TelegramMessageEditPolicy(TelegramMessageEditEntities())
    var allowed = true
    var sends = 0
    var edits = 0
    var optionsReads = 0
    val message = TdApi.Message().apply {
        id = 123
        chatId = 42
        date = 10
        senderId = TdApi.MessageSenderUser(100)
        isOutgoing = true
        content = TdApi.MessageText(TdApi.FormattedText("Original", emptyArray()), null, null)
    }

    override fun messageEditOptions(chatId: String, messageId: String): MessageEditOptions {
        validate(chatId, messageId)
        optionsReads++
        return policy.options(message, "100", 1, 4096)
    }

    override fun editMessage(chatId: String, messageId: String, request: MessageEditRequest): ChatMessage {
        validate(chatId, messageId)
        if (request.revision != policy.revision(message, "100", 1)) {
            throw MessageEditConflictException()
        }
        val formatted = policy.updated(message, request, 4096)
        if (formatted != policy.formatted(message)) {
            edits++
            message.content = TdApi.MessageText(formatted, null, null)
            message.editDate = 100 + edits
        }
        return ChatMessage(
            "123",
            "42",
            formatted.text,
            true,
            10,
            entities = TelegramEntityMapper().map(formatted),
            editedAt = message.editDate.toLong(),
        )
    }

    override fun send(
        chatId: String,
        text: String,
        replyToId: String,
        topicId: Int,
        entities: List<ru.aigram.domain.chat.MessageEntity>,
        automated: Boolean,
    ): String {
        sends++
        error("Editing must never send a new Telegram message")
    }

    private fun validate(chatId: String, messageId: String) {
        if (chatId != "42") {
            throw ChatNotFoundException()
        }
        policy.messageId(messageId)
        policy.verifyOwner(message, TdApi.MessageProperties().apply { canBeEdited = allowed }, "100")
    }
}
