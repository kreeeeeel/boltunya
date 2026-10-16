package ru.boltunya.telegram

import it.tdlight.client.TelegramError
import it.tdlight.jni.TdApi
import jakarta.inject.Inject
import jakarta.inject.Provider
import ru.boltunya.exception.impl.TelegramRateLimitException
import ru.boltunya.exception.impl.TelegramSendFailedException
import ru.boltunya.resources.ResourceCatalog
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

private const val SEND_TIMEOUT_SECONDS = 30L
private const val TYPING_TIMEOUT_SECONDS = 5L
private const val RATE_LIMIT_ERROR = 429
private const val DEFAULT_BACKOFF_SECONDS = 60L
private const val MAX_BACKOFF_SECONDS = 86400L

class TelegramMessageSender @Inject constructor(
    private val gateway: Provider<TdLightGateway>,
    private val resources: ResourceCatalog,
) {
    fun send(chatId: String, text: String, replyToId: String, topicId: Int): String {
        val service = gateway.get()
        val request = TdApi.SendMessage().apply {
            this.chatId = service.parseChatId(chatId)
            if (replyToId.isNotBlank()) replyTo = TdApi.InputMessageReplyToMessage(replyToId.toLong(), null, 0, "")
            if (topicId != 0) this.topicId = TdApi.MessageTopicForum(topicId)
            inputMessageContent = TdApi.InputMessageText().apply { this.text = TdApi.FormattedText(text, emptyArray()) }
        }
        try {
            return service.readyClient().sendMessage(
                request,
                true,
            ).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS).id.toString()
        } catch (
            exception: ExecutionException,
        ) {
            val error = exception.cause
            if (error is TelegramError && error.errorCode == RATE_LIMIT_ERROR) {
                val seconds =
                    resources.regex(
                        "common/telegram-flood-wait",
                    ).find(error.message.orEmpty())?.groupValues?.get(1)?.toLongOrNull()
                        ?: DEFAULT_BACKOFF_SECONDS
                throw TelegramRateLimitException(seconds.coerceIn(1, MAX_BACKOFF_SECONDS), error)
            }
            throw TelegramSendFailedException(exception)
        }
    }

    fun typing(chatId: String, active: Boolean) {
        val service = gateway.get()
        val request = TdApi.SendChatAction().apply {
            this.chatId = service.parseChatId(chatId)
            action =
                if (active) TdApi.ChatActionTyping() else TdApi.ChatActionCancel()
        }
        service.readyClient().send(request).get(TYPING_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }
}
