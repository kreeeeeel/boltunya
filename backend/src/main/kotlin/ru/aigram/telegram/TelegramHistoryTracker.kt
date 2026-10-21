package ru.aigram.telegram

import it.tdlight.jni.TdApi
import jakarta.inject.Inject
import jakarta.inject.Provider
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ChatMessage
import ru.aigram.memory.MemoryCoordinator
import ru.aigram.storage.MessageArchiveStore

class TelegramHistoryTracker @Inject constructor(
    private val archive: MessageArchiveStore,
    private val cache: TelegramCache,
    private val mapper: TelegramMessageMapper,
    private val replies: Provider<ReplyCoordinator>,
    private val memory: Provider<MemoryCoordinator>,
) {
    fun record(message: ChatMessage): ChatMessage {
        val owner = cache.ownId.takeIf { it != 0L }?.toString().orEmpty()
        archive.record(owner, listOf(message))
        return archive.find(owner, message.chatId, message.id) ?: message
    }

    fun edited(update: TdApi.UpdateMessageContent) {
        val owner = cache.ownId.takeIf { it != 0L }?.toString().orEmpty()
        val original = archive.find(owner, update.chatId.toString(), update.messageId.toString()) ?: return
        val content = mapper.map(
            TdApi.Message().apply {
                id = update.messageId
                chatId = update.chatId
                date = original.date.toInt()
                isOutgoing = original.outgoing
                this.content =
                    update.newContent
            },
        )
        archive.record(
            owner,
            listOf(
                original.copy(
                    text = content.text,
                    entities = content.entities,
                    kind = content.kind,
                    media = content.media,
                ),
            ),
        )
    }

    fun deleted(update: TdApi.UpdateDeleteMessages) {
        if (update.isPermanent && !update.fromCache) {
            deleted(cache.ownId.toString(), update.chatId, update.messageIds.toList())
        }
    }

    @Synchronized
    fun deleted(account: String, chatId: Long, ids: List<Long>) {
        if (account == "0" || account.isEmpty()) return
        val changed = try {
            archive.delete(account, chatId.toString(), ids, permanent = true, fromCache = false)
        } catch (exception: Exception) {
            invalidate(account, chatId, ids)
            throw exception
        }
        if (changed) invalidate(account, chatId, ids)
    }

    private fun invalidate(account: String, chatId: Long, ids: List<Long>) {
        if (cache.ownId.toString() != account) return
        cache.updateChat(chatId) { chat ->
            if (ids.stream().anyMatch { it == chat.lastMessage?.id }) chat.lastMessage = null
        }
        replies.get().invalidateContext(chatId.toString())
        val coordinator = memory.get()
        if (coordinator.invalidateContext(chatId.toString())) coordinator.refresh(rebuild = true)
    }
}
