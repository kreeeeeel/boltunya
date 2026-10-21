package ru.aigram.telegram

import it.tdlight.client.SimpleTelegramClient
import it.tdlight.jni.TdApi
import org.springframework.stereotype.Component
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.exception.impl.ChatUnavailableException
import ru.aigram.storage.MessageArchiveStore
import java.util.concurrent.TimeUnit

private const val MAX_PAGE_SIZE = 100
private const val MAX_READ_DEPTH = 20
private const val HISTORY_TIMEOUT_SECONDS = 20L

@Component
class TelegramHistoryReader(
    private val mapper: TelegramMessageView,
    private val cache: TelegramCache,
    private val archive: MessageArchiveStore,
) {
    fun read(client: SimpleTelegramClient, chatId: Long, limit: Int, before: Long): List<ChatMessage> {
        val owner = cache.ownId
        val account = owner.takeIf { it != 0L }?.toString().orEmpty()
        val version = archive.version(account, chatId.toString())
        val messages = page(client, chatId, limit.coerceIn(1, MAX_PAGE_SIZE), before, 0).stream()
            .map(mapper::map).sorted(compareBy { it.id.toLong() }).toList()
        if (cache.ownId != owner) {
            throw ChatUnavailableException()
        }
        return archive.recordPage(account, chatId.toString(), messages, version)
    }

    private fun page(
        client: SimpleTelegramClient,
        chatId: Long,
        limit: Int,
        before: Long,
        depth: Int,
    ): List<TdApi.Message> {
        val request = TdApi.GetChatHistory().apply {
            this.chatId = chatId
            fromMessageId = before
            this.limit =
                (
                    limit + if (before == 0L) {
                        0
                    } else {
                        1
                    }
                    ).coerceAtMost(MAX_PAGE_SIZE)
        }
        val page = client.send(
            request,
        ).get(HISTORY_TIMEOUT_SECONDS, TimeUnit.SECONDS).messages.asList().stream().filter {
            before ==
                0L ||
                it.id < before
        }.limit(limit.toLong()).toList()
        if (page.isEmpty() || page.size >= limit || depth >= MAX_READ_DEPTH) {
            return page
        }
        return page + page(client, chatId, limit - page.size, page.last().id, depth + 1)
    }
}
