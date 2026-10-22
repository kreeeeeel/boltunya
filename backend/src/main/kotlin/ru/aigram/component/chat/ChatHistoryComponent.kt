package ru.aigram.component.chat

import org.springframework.stereotype.Component
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.MessageLink
import ru.aigram.exception.impl.ChatNotFoundException
import ru.aigram.exception.impl.ChatUnavailableException
import ru.aigram.storage.AutomatedReplyStore
import ru.aigram.storage.ContextExclusionStore
import ru.aigram.storage.MessageArchiveStore
import ru.aigram.telegram.MediaDownloadPolicy
import ru.aigram.telegram.TelegramGateway
import ru.aigram.telegram.TelegramMediaStore

private const val HISTORY_PAGE_SIZE = 100

@Component
class ChatHistoryComponent(
    private val telegram: TelegramGateway,
    private val automated: AutomatedReplyStore,
    private val archive: MessageArchiveStore,
    private val policy: MediaDownloadPolicy,
    private val media: TelegramMediaStore,
    private val exclusions: ContextExclusionStore,
) {
    fun history(chatId: String, before: String?): List<ChatMessage> {
        val cursor = before?.toLongOrNull() ?: 0
        val owner = telegram.status().userId
        val version = archive.version(owner, chatId)
        val current = telegram.history(chatId, HISTORY_PAGE_SIZE, cursor)
        if (telegram.status().userId != owner) {
            throw ChatUnavailableException()
        }
        val retained = archive.recordPage(owner, chatId, current, version)
        val messages = archive.merge(owner, chatId, retained, cursor, HISTORY_PAGE_SIZE)
            .stream().map(::decorate).toList()
        return exclusions.decorate(owner, messages)
    }

    fun deleted(chatId: String, before: String?): List<ChatMessage> {
        if (telegram.chats().stream().noneMatch { it.id == chatId }) {
            throw ChatNotFoundException()
        }
        val owner = telegram.status().userId
        val messages = archive.deleted(owner, chatId, before?.toLongOrNull() ?: 0, HISTORY_PAGE_SIZE)
            .stream().map(::decorate).toList()
        return exclusions.decorate(owner, messages)
    }

    fun link(chatId: String, messageId: String): MessageLink = MessageLink(telegram.messageLink(chatId, messageId))

    fun present(message: ChatMessage): ChatMessage =
        exclusions.decorate(telegram.status().userId, listOf(decorate(message))).single()

    private fun decorate(message: ChatMessage): ChatMessage =
        policy.decorate(message.copy(automated = automated.contains(message.chatId, message.id)), media)
}
