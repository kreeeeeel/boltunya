package ru.aigram.http.endpoint

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component
import ru.aigram.ai.ConversationService
import ru.aigram.domain.ChatMessage
import ru.aigram.domain.ChatRule
import ru.aigram.domain.ReplyResult
import ru.aigram.exception.impl.ModelSettingsRequiredException
import ru.aigram.http.InputValidation
import ru.aigram.http.RequestReader
import ru.aigram.memory.MemoryCoordinator
import ru.aigram.storage.AutomatedReplyStore
import ru.aigram.storage.ContextExclusionStore
import ru.aigram.storage.SettingsStore
import ru.aigram.storage.WritingStyleStore
import ru.aigram.telegram.TelegramGateway

private const val STYLE_HISTORY_SIZE = 100
private const val MAX_MESSAGE_CHARACTERS = 4096

@Component
class PreviewEndpoint @Inject constructor(
    private val json: Json,
    private val settings: SettingsStore,
    private val telegram: TelegramGateway,
    private val conversation: ConversationService,
    private val automated: AutomatedReplyStore,
    private val writing: WritingStyleStore,
    private val validation: InputValidation,
    private val input: RequestReader,
    private val memory: MemoryCoordinator,
    private val exclusions: ContextExclusionStore,
) {
    fun reply(body: JsonObject): ReplyResult {
        val submitted = json.decodeFromJsonElement<ChatRule>(body["rule"] ?: throw ModelSettingsRequiredException())
        val rule = settings.resolveModel(submitted)
        validation.validate(rule)
        validation.target(rule)
        val id = body["chatId"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val history = if (id.isBlank()) {
            emptyList()
        } else {
            telegram.history(
                id,
                if (rule.matchMyStyle) STYLE_HISTORY_SIZE else rule.contextMessages,
            ).map { it.copy(automated = it.automated || automated.contains(it.chatId, it.id)) }
        }
        val message = ChatMessage("preview", id, input.text(body, "text", MAX_MESSAGE_CHARACTERS), false, 0)
        val owner = telegram.status().userId
        fun eligible(messages: List<ChatMessage>): List<ChatMessage> =
            if (id.isBlank()) messages else exclusions.forInference(owner, messages)
        val context = eligible(
            if (history.lastOrNull()?.let { !it.outgoing && it.text == message.text } ==
                true
            ) {
                history
            } else {
                history + message
            },
        )
        val samples = eligible(history.filter { !it.automated })
        return conversation.reply(
            settings.resolve(rule),
            context,
            settings.get().globalPrompt,
            if (rule.matchMyStyle &&
                id.isNotBlank()
            ) {
                eligible(writing.update(id, samples))
            } else {
                emptyList()
            },
            memory.context(rule, context),
        )
    }
}
