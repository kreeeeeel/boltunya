package ru.boltunya.http.endpoint

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import ru.boltunya.ai.EngineManager
import ru.boltunya.automation.ReplyCoordinator
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.exception.impl.ChatNotFoundException
import ru.boltunya.http.InputValidation
import ru.boltunya.http.RequestReader
import ru.boltunya.storage.AutomatedReplyStore
import ru.boltunya.storage.MessageArchiveStore
import ru.boltunya.storage.SettingsStore
import ru.boltunya.telegram.MediaDownloadPolicy
import ru.boltunya.telegram.TelegramGateway
import ru.boltunya.telegram.TelegramMediaStore

private const val HISTORY_PAGE_SIZE = 100
private const val MAX_MESSAGE_CHARACTERS = 4096
private const val REPLY_HISTORY_SIZE = 20

class ChatEndpoint @Inject constructor(
    private val json: Json,
    private val settings: SettingsStore,
    private val telegram: TelegramGateway,
    private val engine: EngineManager,
    private val replies: ReplyCoordinator,
    private val automated: AutomatedReplyStore,
    private val validation: InputValidation,
    private val input: RequestReader,
    private val archive: MessageArchiveStore,
    private val policy: MediaDownloadPolicy,
    private val media: TelegramMediaStore,
) {
    fun history(id: String, before: Long): List<ChatMessage> {
        val current = telegram.history(id, HISTORY_PAGE_SIZE, before)
        val owner = telegram.status().userId
        archive.record(owner, current)
        return archive.merge(owner, id, current, before, HISTORY_PAGE_SIZE).map(::decorate)
    }

    fun deleted(id: String, before: Long): List<ChatMessage> {
        if (telegram.chats().none { it.id == id }) throw ChatNotFoundException()
        return archive.deleted(telegram.status().userId, id, before, HISTORY_PAGE_SIZE).map(::decorate)
    }

    private fun decorate(message: ChatMessage): ChatMessage =
        policy.decorate(message.copy(automated = automated.contains(message.chatId, message.id)), media)

    fun send(id: String, body: JsonObject) {
        replies.onMessage(ChatMessage("manual-${System.nanoTime()}", id, "", true, 0))
        val target = telegram.history(id, REPLY_HISTORY_SIZE).lastOrNull { !it.outgoing && !it.deleted }
        telegram.send(id, input.text(body, "text", MAX_MESSAGE_CHARACTERS), target?.id.orEmpty(), target?.topicId ?: 0)
    }

    fun saveRule(id: String, body: JsonObject) {
        if (telegram.chats().stream().noneMatch { it.id == id }) throw ChatNotFoundException()
        val rule = json.decodeFromJsonElement<ChatRule>(body)
        validation.validate(rule)
        val resolved = settings.resolveModel(rule)
        validation.model(resolved.model, forAutomation = rule.enabled)
        settings.saveRule(id, rule)
        replies.cancelPending()
        engine.warm(resolved.model)
    }
}
