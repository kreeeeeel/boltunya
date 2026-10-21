package ru.aigram.component.models

import org.springframework.stereotype.Component
import ru.aigram.ai.ConversationService
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatRule
import ru.aigram.exception.impl.InvalidFieldLengthException
import ru.aigram.memory.MemoryCoordinator
import ru.aigram.storage.AutomatedReplyStore
import ru.aigram.storage.ContextExclusionStore
import ru.aigram.storage.SettingsStore
import ru.aigram.storage.WritingStyleStore
import ru.aigram.telegram.TelegramGateway
import ru.aigram.validation.InputValidation

private const val STYLE_HISTORY_SIZE = 100
private const val MAX_MESSAGE_CHARACTERS = 4096

@Component
class PreviewComponent(
    private val settings: SettingsStore,
    private val telegram: TelegramGateway,
    private val conversation: ConversationService,
    private val automated: AutomatedReplyStore,
    private val writing: WritingStyleStore,
    private val validation: InputValidation,
    private val memory: MemoryCoordinator,
    private val exclusions: ContextExclusionStore,
) {
    fun reply(submitted: ChatRule, chatId: String, text: String): ReplyResult {
        val trimmed = text.trim()
        if (trimmed.isBlank() || trimmed.length > MAX_MESSAGE_CHARACTERS) {
            throw InvalidFieldLengthException("text", MAX_MESSAGE_CHARACTERS)
        }
        val rule = settings.resolveModel(submitted)
        validation.validate(rule)
        validation.target(rule)
        val history = if (chatId.isBlank()) {
            emptyList()
        } else {
            val limit = if (rule.matchMyStyle) {
                STYLE_HISTORY_SIZE
            } else {
                rule.contextMessages
            }
            telegram.history(chatId, limit).stream().map {
                it.copy(automated = it.automated || automated.contains(it.chatId, it.id))
            }.toList()
        }
        val message = ChatMessage("preview", chatId, trimmed, false, 0)
        val owner = telegram.status().userId
        fun eligible(messages: List<ChatMessage>): List<ChatMessage> = if (chatId.isBlank()) {
            messages
        } else {
            exclusions.forInference(owner, messages)
        }
        val last = history.lastOrNull()
        val context = eligible(
            if (last != null && !last.outgoing && last.text == message.text) {
                history
            } else {
                history + message
            },
        )
        val samples = eligible(history.stream().filter { !it.automated }.toList())
        val styleSamples = if (rule.matchMyStyle && chatId.isNotBlank()) {
            eligible(writing.update(chatId, samples))
        } else {
            emptyList()
        }
        return conversation.reply(
            settings.resolve(rule),
            context,
            settings.get().globalPrompt,
            styleSamples,
            memory.context(rule, context),
        )
    }
}
