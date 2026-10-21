package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import ru.aigram.domain.media.OutgoingMedia
import ru.aigram.exception.impl.TelegramSendFailedException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private const val MEDIA_SEND_TIMEOUT_SECONDS = 60L

@Component
class TelegramMediaSender(
    private val gateway: ObjectProvider<TdLightGateway>,
    private val content: TelegramMediaContent,
    private val delivery: TelegramMediaDeliveryTracker,
) {
    fun send(chatId: String, media: List<OutgoingMedia>, replyToId: String = "", topicId: Int = 0): List<String> {
        val service = gateway.getObject()
        val ownerId = service.status().userId
        val request = content.request(service.parseChatId(chatId), media, replyToId, topicId)
        val client = service.readyClient()
        return try {
            when (request) {
                is TdApi.SendMessage -> listOf(
                    client.sendMessage(request, true).get(MEDIA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS).id.toString(),
                )
                is TdApi.SendMessageAlbum -> {
                    val messages = client.send(
                        request,
                    ).get(MEDIA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS).messages.filterNotNull()
                    if (messages.size != media.size) {
                        throw TelegramSendFailedException()
                    }
                    delivery.await(ownerId, messages)
                }
                else -> throw TelegramSendFailedException()
            }
        } catch (exception: ExecutionException) {
            throw TelegramSendFailedException(exception)
        } catch (exception: TimeoutException) {
            throw TelegramSendFailedException(exception)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw TelegramSendFailedException(exception)
        }
    }
}
