package ru.aigram.component.chat

import org.springframework.stereotype.Component
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.MessageLink
import ru.aigram.domain.media.ChatGallery
import ru.aigram.exception.impl.ChatNotFoundException
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
        val current = telegram.history(chatId, HISTORY_PAGE_SIZE, cursor)
        val owner = telegram.status().userId
        archive.record(owner, current)
        val messages = archive.merge(owner, chatId, current, cursor, HISTORY_PAGE_SIZE)
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

    fun gallery(chatId: String, around: String): ChatGallery {
        val page = telegram.gallery(chatId, around)
        val owner = telegram.status().userId
        archive.record(owner, page.messages)
        return page.copy(messages = exclusions.decorate(owner, page.messages.stream().map(::decorate).toList()))
    }

    fun link(chatId: String, messageId: String): MessageLink = MessageLink(telegram.messageLink(chatId, messageId))

    private fun decorate(message: ChatMessage): ChatMessage =
        policy.decorate(message.copy(automated = automated.contains(message.chatId, message.id)), media)
}
