package ru.aigram.http.endpoint

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ContextExclusionUpdate
import ru.aigram.exception.impl.ChatNotFoundException
import ru.aigram.memory.MemoryCoordinator
import ru.aigram.storage.ContextExclusionStore
import ru.aigram.telegram.TelegramGateway

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
        memory.invalidateContext(chatId)
        // Owner facts can originate in any tracked conversation. Rebuild those sources, preserving manual notes.
        memory.refresh(rebuild = true)
    }
}
