package ru.aigram.telegram

import it.tdlight.client.SimpleTelegramClient
import it.tdlight.client.TelegramError
import it.tdlight.jni.TdApi
import jakarta.inject.Inject
import ru.aigram.exception.impl.ChatListLoadFailedException
import java.util.concurrent.CompletionException

private const val CHAT_BATCH_SIZE = 200
private const val NO_MORE_CHATS = 404

class TelegramChatLoader @Inject constructor(private val connection: TelegramConnectionState) {
    fun load(client: SimpleTelegramClient, list: TdApi.ChatList, version: Long) {
        if (connection.version != version) return
        client.send(TdApi.LoadChats(list, CHAT_BATCH_SIZE)).whenCompleteAsync { _, failure ->
            if (connection.version != version) return@whenCompleteAsync
            if (failure == null) {
                load(client, list, version)
            } else {
                val cause = if (failure is CompletionException) failure.cause else failure
                if (cause !is TelegramError ||
                    cause.errorCode != NO_MORE_CHATS
                ) {
                    connection.status =
                        connection.status.copy(error = ChatListLoadFailedException().message)
                }
            }
        }
    }
}
