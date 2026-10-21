package ru.aigram.component.chat

import org.springframework.stereotype.Component
import ru.aigram.ai.EngineManager
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.automation.ReplyDeliveryPolicy
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.chat.MessageEntity
import ru.aigram.domain.chat.ProactiveMessageMode
import ru.aigram.domain.chat.ProactiveMode
import ru.aigram.exception.impl.ChatNotFoundException
import ru.aigram.exception.impl.InvalidFieldLengthException
import ru.aigram.exception.impl.ProactiveTextRequiredException
import ru.aigram.storage.SettingsStore
import ru.aigram.telegram.TelegramGateway
import ru.aigram.telegram.TelegramOutgoingEntities
import ru.aigram.validation.InputValidation

private const val MAX_MESSAGE_CHARACTERS = 4096
private const val REPLY_HISTORY_SIZE = 20

@Component
class ChatCommunicationComponent(
    private val settings: SettingsStore,
    private val telegram: TelegramGateway,
    private val engine: EngineManager,
    private val replies: ReplyCoordinator,
    private val validation: InputValidation,
    private val delivery: ReplyDeliveryPolicy,
    private val outgoingEntities: TelegramOutgoingEntities,
) {
    fun send(chatId: String, value: String, entities: List<MessageEntity> = emptyList()) {
        val text = if (entities.isEmpty()) {
            value.trim()
        } else {
            value
        }
        if (text.isBlank() || text.length > MAX_MESSAGE_CHARACTERS) {
            throw InvalidFieldLengthException("text", MAX_MESSAGE_CHARACTERS)
        }
        outgoingEntities.format(text, entities)
        replies.onMessage(ChatMessage("manual-${System.nanoTime()}", chatId, "", true, 0))
        val target = telegram.history(chatId, REPLY_HISTORY_SIZE).lastOrNull { !it.outgoing && !it.deleted }
        telegram.send(
            chatId,
            text,
            delivery.replyTo(settings.rule(chatId), target?.id.orEmpty()),
            target?.topicId ?: 0,
            entities,
        )
    }

    fun saveRule(chatId: String, rule: ChatRule) {
        if (telegram.chats().stream().noneMatch { it.id == chatId }) {
            throw ChatNotFoundException()
        }
        validation.validate(rule)
        if (rule.proactiveMode != ProactiveMode.DISABLED &&
            rule.proactiveMessageMode == ProactiveMessageMode.TEXT && rule.proactiveText.isBlank()
        ) {
            throw ProactiveTextRequiredException()
        }
        val resolved = settings.resolveModel(rule)
        val usesModel = rule.enabled || rule.proactiveMessageMode == ProactiveMessageMode.AI
        if (usesModel) {
            validation.target(resolved, forAutomation = rule.enabled || rule.proactiveMode == ProactiveMode.ENABLED)
        }
        val previous = settings.rule(chatId)
        settings.saveRule(chatId, rule)
        if (settings.rule(chatId) != previous) {
            replies.cancelReplies(chatId)
        }
        val model = if (usesModel) {
            validation.localModel(resolved)
        } else {
            null
        }
        if (model != null) {
            engine.warm(model)
        }
    }
}
