package ru.aigram.component.chat

import org.springframework.stereotype.Component
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.chat.ContextExclusionUpdate
import ru.aigram.exception.impl.ChatNotFoundException
import ru.aigram.memory.MemoryCoordinator
import ru.aigram.storage.ContextExclusionStore
import ru.aigram.telegram.TelegramGateway

@Component
class ContextExclusionComponent(
    private val exclusions: ContextExclusionStore,
    private val telegram: TelegramGateway,
    private val replies: ReplyCoordinator,
    private val memory: MemoryCoordinator,
) {
    fun update(chatId: String, messageId: String, update: ContextExclusionUpdate) {
        validateChat(chatId)
        synchronized(exclusions) {
            exclusions.update(telegram.status().userId, chatId, messageId, update)
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
        if (telegram.chats().stream().noneMatch { it.id == chatId }) {
            throw ChatNotFoundException()
        }
    }

    private fun changed(chatId: String) {
        replies.invalidateContext(chatId)
        memory.invalidateContext(chatId)
    }
}
