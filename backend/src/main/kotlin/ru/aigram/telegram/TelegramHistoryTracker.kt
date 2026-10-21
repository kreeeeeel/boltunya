package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.exception.impl.MessageEditConflictException
import ru.aigram.memory.MemoryCoordinator
import ru.aigram.storage.MessageArchiveStore
import ru.aigram.storage.WritingStyleStore
import java.util.stream.Collectors

@Component
class TelegramHistoryTracker(
    private val archive: MessageArchiveStore,
    private val cache: TelegramCache,
    private val mapper: TelegramMessageMapper,
    private val replies: ObjectProvider<ReplyCoordinator>,
    private val memory: ObjectProvider<MemoryCoordinator>,
    private val writing: WritingStyleStore,
) {
    fun record(message: ChatMessage): ChatMessage {
        val owner = cache.ownId.takeIf { it != 0L }?.toString().orEmpty()
        archive.record(owner, listOf(message))
        return archive.find(owner, message.chatId, message.id) ?: message
    }

    fun edited(update: TdApi.UpdateMessageContent) {
        val owner = cache.ownId.takeIf { it != 0L }?.toString().orEmpty()
        if (owner.isEmpty()) {
            return
        }
        cache.updateChat(update.chatId) { chat ->
            val last = chat.lastMessage
            if (last?.id == update.messageId) {
                last.content = update.newContent
            }
        }
        val original = archive.find(owner, update.chatId.toString(), update.messageId.toString())
        if (original == null) {
            invalidateEdited(owner, update.chatId, update.messageId)
            return
        }

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
        val changed = original.copy(
            text = content.text,
            entities = content.entities,
            kind = content.kind,
            media = content.media,
        )
        if (changed == original) {
            return
        }
        try {
            archive.recordEdited(owner, listOf(changed))
        } finally {
            invalidateEdited(owner, update.chatId, update.messageId)
        }
    }

    fun edited(update: TdApi.UpdateMessageEdited) {
        val owner = cache.ownId.takeIf { it != 0L }?.toString().orEmpty()
        if (owner.isEmpty()) {
            return
        }
        cache.updateChat(update.chatId) { chat ->
            val last = chat.lastMessage
            if (last?.id == update.messageId) {
                last.editDate = update.editDate
                last.replyMarkup = update.replyMarkup
            }
        }
        val previous = archive.find(owner, update.chatId.toString(), update.messageId.toString()) ?: return
        if (previous.editedAt < update.editDate) {
            archive.recordEdited(owner, listOf(previous.copy(editedAt = update.editDate.toLong())))
        }
    }

    fun edited(account: String, raw: TdApi.Message, mapped: ChatMessage): ChatMessage {
        if (account.isEmpty() || account == "0" || cache.ownId.toString() != account) {
            throw MessageEditConflictException()
        }
        cache.updateChat(raw.chatId) { chat ->
            if (chat.lastMessage?.id == raw.id) {
                chat.lastMessage = raw
            }
        }
        val previous = archive.find(account, mapped.chatId, mapped.id)
        val updated = mapped.copy(automated = mapped.automated || previous?.automated == true)
        if (previous == updated) {
            return updated
        }
        try {
            archive.recordEdited(account, listOf(updated))
        } finally {
            if (previous == null || previous.text != updated.text || previous.entities != updated.entities ||
                previous.kind != updated.kind || previous.media != updated.media
            ) {
                invalidateEdited(account, raw.chatId, raw.id)
            }
        }
        return archive.find(account, mapped.chatId, mapped.id) ?: updated
    }

    fun deleted(update: TdApi.UpdateDeleteMessages) {
        if (update.isPermanent && !update.fromCache) {
            deleted(cache.ownId.toString(), update.chatId, update.messageIds.toList())
        }
    }

    @Synchronized
    fun deleted(account: String, chatId: Long, ids: List<Long>) {
        if (account == "0" || account.isEmpty()) {
            return
        }
        val changed = try {
            archive.delete(account, chatId.toString(), ids, permanent = true, fromCache = false)
        } catch (exception: Exception) {
            invalidate(account, chatId, ids)
            throw exception
        }
        if (changed) {
            invalidate(account, chatId, ids)
        }
    }

    private fun invalidate(account: String, chatId: Long, ids: List<Long>) {
        if (cache.ownId.toString() != account) {
            return
        }
        cache.updateChat(chatId) { chat ->
            if (ids.stream().anyMatch { it == chat.lastMessage?.id }) {
                chat.lastMessage = null
            }
        }
        replies.getObject().invalidateContext(chatId.toString())
        memory.getObject().invalidateContext(chatId.toString())
        writing.forget(chatId.toString(), ids.stream().map(Long::toString).collect(Collectors.toSet()))
    }

    private fun invalidateEdited(account: String, chatId: Long, id: Long) {
        if (cache.ownId.toString() != account) {
            return
        }
        replies.getObject().invalidateContext(chatId.toString())
        memory.getObject().invalidateContext(chatId.toString())
        writing.forget(chatId.toString(), setOf(id.toString()))
    }
}
