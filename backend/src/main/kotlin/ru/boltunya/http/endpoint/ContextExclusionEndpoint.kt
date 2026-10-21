package ru.boltunya.http.endpoint

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import ru.boltunya.automation.ReplyCoordinator
import ru.boltunya.domain.ContextExclusionUpdate
import ru.boltunya.exception.impl.ChatNotFoundException
import ru.boltunya.memory.MemoryCoordinator
import ru.boltunya.storage.ContextExclusionStore
import ru.boltunya.telegram.TelegramGateway

class ContextExclusionEndpoint @Inject constructor(
    private val json: Json,
    private val exclusions: ContextExclusionStore,
    private val telegram: TelegramGateway,
    private val replies: ReplyCoordinator,
    private val memory: MemoryCoordinator,
) {
    fun update(chatId: String, messageId: String, body: JsonObject) {
        validateChat(chatId)
        synchronized(exclusions) {
            exclusions.update(
                telegram.status().userId,
                chatId,
                messageId,
                json.decodeFromJsonElement<ContextExclusionUpdate>(body),
            )
            changed(chatId)
        }
    }

    fun clear(chatId: String) {
        validateChat(chatId)
        synchronized(exclusions) {
            exclusions.clear(telegram.status().userId, chatId)
            changed(chatId)
        }
    }

    private fun validateChat(chatId: String) {
        if (telegram.chats().none { it.id == chatId }) throw ChatNotFoundException()
    }

    private fun changed(chatId: String) {
        replies.invalidateContext(chatId)
        // Owner facts can originate in any tracked conversation. Rebuild those sources, preserving manual notes.
        memory.refresh(rebuild = true)
    }
}
