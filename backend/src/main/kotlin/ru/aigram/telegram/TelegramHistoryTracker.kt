package ru.aigram.telegram

import it.tdlight.jni.TdApi
import jakarta.inject.Inject
import ru.aigram.domain.ChatMessage
import ru.aigram.storage.MessageArchiveStore

class TelegramHistoryTracker @Inject constructor(
    private val archive: MessageArchiveStore,
    private val cache: TelegramCache,
    private val mapper: TelegramMessageMapper,
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
        archive.delete(
            cache.ownId.takeIf {
                it != 0L
            }?.toString().orEmpty(),
            update.chatId.toString(),
            update.messageIds.toList(),
            update.isPermanent,
            update.fromCache,
        )
    }
}
