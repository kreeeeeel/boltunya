package ru.aigram.telegram

import it.tdlight.client.TelegramError
import it.tdlight.jni.TdApi
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import ru.aigram.domain.chat.MessageEntity
import ru.aigram.exception.impl.TelegramRateLimitException
import ru.aigram.exception.impl.TelegramSendFailedException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

private const val SEND_TIMEOUT_SECONDS = 30L
private const val TYPING_TIMEOUT_SECONDS = 5L
private const val RATE_LIMIT_ERROR = 429
private const val DEFAULT_BACKOFF_SECONDS = 60L
private const val MAX_BACKOFF_SECONDS = 86400L

@Component
class TelegramMessageSender(
    private val gateway: ObjectProvider<TdLightGateway>,
    private val entities: TelegramOutgoingEntities,
    private val automatedSends: TelegramAutomatedSendRegistry,
) {
    fun send(
        chatId: String,
        text: String,
        replyToId: String,
        topicId: Int,
        entities: List<MessageEntity> = emptyList(),
        automated: Boolean = false,
    ): String {
        val formatted = this.entities.format(text, entities)
        val service = gateway.getObject()
        val request = TdApi.SendMessage().apply {
            this.chatId = service.parseChatId(chatId)
            if (replyToId.isNotBlank()) {
                replyTo = TdApi.InputMessageReplyToMessage(replyToId.toLong(), null, 0, "")
            }
            if (topicId != 0) {
                this.topicId = TdApi.MessageTopicForum(topicId)
            }
            inputMessageContent = TdApi.InputMessageText().apply { this.text = formatted }
        }
        val client = service.readyClient()
        val send = if (automated) {
            automatedSends.begin(request.chatId)
        } else {
            null
        }
        if (send != null) {
            request.options = TdApi.MessageSendOptions().apply { sendingId = send.sendingId }
        }
        try {
            val message = client.sendMessage(
                request,
                true,
            ).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (send != null) {
                automatedSends.completed(send, message)
            }
            return message.id.toString()
        } catch (
            exception: ExecutionException,
        ) {
            if (send != null) {
                automatedSends.rejected(send)
            }
            val error = exception.cause
            if (error is TelegramError && error.errorCode == RATE_LIMIT_ERROR) {
                val seconds = floodWait(error.message.orEmpty()) ?: DEFAULT_BACKOFF_SECONDS
                throw TelegramRateLimitException(seconds.coerceIn(1, MAX_BACKOFF_SECONDS), error)
            }
            throw TelegramSendFailedException(exception)
        }
    }

    internal fun floodWait(message: String): Long? {
        val text = message.lowercase(java.util.Locale.ROOT)
        val flood = text.indexOf("flood_wait")
        val retry = text.indexOf("retry after")
        val suffix = when {
            flood >= 0 -> text.substring(flood + "flood_wait".length).removePrefix("_")
            retry >= 0 -> text.substring(retry + "retry after".length).trimStart()
            else -> return null
        }
        val end = suffix.indexOfFirst { it !in '0'..'9' }.let {
            if (it < 0) {
                suffix.length
            } else {
                it
            }
        }
        return suffix.substring(0, end).toLongOrNull()
    }

    fun typing(chatId: String, active: Boolean) {
        val service = gateway.getObject()
        val request = TdApi.SendChatAction().apply {
            this.chatId = service.parseChatId(chatId)
            action =
                if (active) {
                    TdApi.ChatActionTyping()
                } else {
                    TdApi.ChatActionCancel()
                }
        }
        service.readyClient().send(request).get(TYPING_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }
}
