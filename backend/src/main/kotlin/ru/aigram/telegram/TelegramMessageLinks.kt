package ru.aigram.telegram

import it.tdlight.jni.TdApi
import jakarta.inject.Inject
import jakarta.inject.Provider
import org.springframework.stereotype.Component
import ru.aigram.exception.impl.InvalidMediaIdentifierException
import ru.aigram.exception.impl.TelegramRequestRejectedException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private const val MESSAGE_LINK_TIMEOUT_SECONDS = 10L

@Component
class TelegramMessageLinks @Inject constructor(private val gateway: Provider<TdLightGateway>) {
    fun link(chatId: String, messageId: String): String {
        val service = gateway.get()
        val request = request(service.parseChatId(chatId), messageId)
        return try {
            val link = service.readyClient().send(request).get(MESSAGE_LINK_TIMEOUT_SECONDS, TimeUnit.SECONDS).link
            if (link.isNullOrBlank()) throw TelegramRequestRejectedException()
            link
        } catch (_: ExecutionException) {
            throw TelegramRequestRejectedException()
        } catch (_: TimeoutException) {
            throw TelegramRequestRejectedException()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw TelegramRequestRejectedException()
        }
    }

    internal fun request(chatId: Long, messageId: String): TdApi.GetMessageLink {
        val id = messageId.toLongOrNull()?.takeIf { it > 0 } ?: throw InvalidMediaIdentifierException()
        return TdApi.GetMessageLink(chatId, id, 0, 0, "", false, false)
    }
}
