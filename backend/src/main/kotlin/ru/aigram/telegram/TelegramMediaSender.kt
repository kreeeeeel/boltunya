package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import ru.aigram.domain.media.OutgoingMedia
import ru.aigram.exception.impl.TelegramNotConnectedException
import ru.aigram.exception.impl.TelegramSendFailedException
import java.nio.file.Path
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private const val MEDIA_SEND_TIMEOUT_SECONDS = 60L
private const val ALBUM_SEND_TIMEOUT_SECONDS = 240L

@Component
class TelegramMediaSender(
    private val gateway: ObjectProvider<TdLightGateway>,
    private val content: TelegramMediaContent,
    private val delivery: TelegramMediaDeliveryTracker,
    private val outgoingFiles: TelegramOutgoingFileStore,
    private val connection: TelegramConnectionState,
) {
    fun send(chatId: String, media: List<OutgoingMedia>, replyToId: String = "", topicId: Int = 0): List<String> {
        val service = gateway.getObject()
        val version = connection.version
        val ownerId = service.status().userId
        val request = content.request(service.parseChatId(chatId), media, replyToId, topicId)
        val client = service.readyClient()
        val retained = retainInputs(request)
        var dispatched = false
        return try {
            when (request) {
                is TdApi.SendMessage -> {
                    if (connection.version != version || service.status().userId != ownerId) {
                        throw TelegramNotConnectedException()
                    }
                    val future = client.sendMessage(request, true)
                    outgoingFiles.track(retained, future)
                    dispatched = true
                    listOf(future.get(MEDIA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS).id.toString())
                }
                is TdApi.SendMessageAlbum -> {
                    if (connection.version != version || service.status().userId != ownerId) {
                        throw TelegramNotConnectedException()
                    }
                    val future = client.send(request).thenCompose { result ->
                        val messages = result.messages.filterNotNull()
                        if (messages.size != media.size) {
                            throw TelegramSendFailedException()
                        }
                        delivery.track(ownerId, messages)
                    }
                    outgoingFiles.track(retained, future)
                    dispatched = true
                    future.get(ALBUM_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
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
        } finally {
            if (!dispatched) {
                retained.stream().forEach(outgoingFiles::completed)
            }
        }
    }

    internal fun retainInputs(request: TdApi.Function<*>): List<Path> {
        // Keep partial ownership so a later copy failure can release only inputs not yet given to TDLib.
        val retained = mutableListOf<Path>()
        try {
            val inputs = when (request) {
                is TdApi.SendMessage -> listOf(request.inputMessageContent)
                is TdApi.SendMessageAlbum -> request.inputMessageContents.toList()
                else -> throw TelegramSendFailedException()
            }
            inputs.stream().forEach { retained.add(retain(it)) }
            return retained
        } catch (exception: Exception) {
            retained.stream().forEach(outgoingFiles::completed)
            throw exception
        }
    }

    private fun retain(input: TdApi.InputMessageContent): Path {
        val file = when (input) {
            is TdApi.InputMessagePhoto -> input.photo.photo
            is TdApi.InputMessageVideo -> input.video.video
            is TdApi.InputMessageDocument -> input.document.document
            else -> throw TelegramSendFailedException()
        }
        if (file !is TdApi.InputFileLocal) {
            throw TelegramSendFailedException()
        }
        val retained = outgoingFiles.retain(Path.of(file.path))
        file.path = retained.toString()
        return retained
    }
}
