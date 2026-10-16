package ru.boltunya.http.endpoint

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import ru.boltunya.ai.ConversationService
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.domain.ReplyResult
import ru.boltunya.exception.impl.ModelSettingsRequiredException
import ru.boltunya.http.InputValidation
import ru.boltunya.http.RequestReader
import ru.boltunya.memory.MemoryCoordinator
import ru.boltunya.storage.AutomatedReplyStore
import ru.boltunya.storage.SettingsStore
import ru.boltunya.storage.WritingStyleStore
import ru.boltunya.telegram.TelegramGateway

private const val STYLE_HISTORY_SIZE = 100
private const val MAX_MESSAGE_CHARACTERS = 4096

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
) {
    fun reply(body: JsonObject): ReplyResult {
        val submitted = json.decodeFromJsonElement<ChatRule>(body["rule"] ?: throw ModelSettingsRequiredException())
        val rule = settings.resolveModel(submitted)
        validation.validate(rule)
        validation.model(rule.model)
        val id = body["chatId"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val history = if (id.isBlank()) {
            emptyList()
        } else {
            telegram.history(
                id,
                if (rule.matchMyStyle) STYLE_HISTORY_SIZE else rule.contextMessages,
            )
        }
        val message = ChatMessage("preview", id, input.text(body, "text", MAX_MESSAGE_CHARACTERS), false, 0)
        val examples = history.filter { !automated.contains(it.chatId, it.id) }
        return conversation.reply(
            settings.resolve(rule),
            history + message,
            settings.get().globalPrompt,
            if (rule.matchMyStyle &&
                id.isNotBlank()
            ) {
                writing.update(id, examples)
            } else {
                emptyList()
            },
            memory.context(rule, history + message),
        )
    }
}
