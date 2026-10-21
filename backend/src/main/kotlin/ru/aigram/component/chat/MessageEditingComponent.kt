package ru.aigram.component.chat

import org.springframework.stereotype.Component
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.MessageEditOptions
import ru.aigram.domain.chat.MessageEditRequest
import ru.aigram.telegram.TelegramGateway
import ru.aigram.validation.InputValidation

@Component
class MessageEditingComponent(
    private val telegram: TelegramGateway,
    private val history: ChatHistoryComponent,
    private val validation: InputValidation,
) {
    fun options(chatId: String, messageId: String): MessageEditOptions = telegram.messageEditOptions(chatId, messageId)

    fun edit(chatId: String, messageId: String, request: MessageEditRequest): ChatMessage {
        validation.validate(request)
        val updated = telegram.editMessage(chatId, messageId, request)
        return history.present(updated)
    }
}
