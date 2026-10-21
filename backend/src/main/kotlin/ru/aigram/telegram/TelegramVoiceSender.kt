package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import ru.aigram.domain.speech.OutgoingVoiceNote
import ru.aigram.exception.impl.TelegramNotConnectedException
import ru.aigram.exception.impl.TelegramSendFailedException
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private const val VOICE_SEND_TIMEOUT_SECONDS = 60L

@Component
class TelegramVoiceSender(
    private val gateway: ObjectProvider<TdLightGateway>,
    private val content: TelegramVoiceContent,
    private val automatedSends: TelegramAutomatedSendRegistry,
    private val connection: TelegramConnectionState,
    private val outgoingFiles: TelegramOutgoingFileStore,
) {
    fun send(
        chatId: String,
        voice: OutgoingVoiceNote,
        replyToId: String = "",
        topicId: Int = 0,
        automated: Boolean = false,
    ): String {
        if (Thread.currentThread().isInterrupted) {
            throw CancellationException()
        }
        val version = connection.version
        val service = gateway.getObject()
        val owner = service.status().userId
        if (voice.ownerId.isNotEmpty() && voice.ownerId != owner) {
            throw TelegramNotConnectedException()
        }
        val request = content.request(service.parseChatId(chatId), voice, replyToId, topicId)
        val client = service.readyClient()
        val send = if (automated) {
            automatedSends.begin(request.chatId)
        } else {
            null
        }
        val retained = try {
            outgoingFiles.retain(voice.path)
        } catch (exception: Exception) {
            if (send != null) {
                automatedSends.rejected(send)
            }
            throw exception
        }
        val input = request.inputMessageContent as TdApi.InputMessageVoiceNote
        input.voiceNote.voiceNote = TdApi.InputFileLocal(retained.toString())
        if (connection.version != version || service.status().userId != owner ||
            send != null && (send.version != version || send.ownerId != owner)
        ) {
            if (send != null) {
                automatedSends.rejected(send)
            }
            outgoingFiles.completed(retained)
            throw TelegramNotConnectedException()
        }
        if (send != null) {
            request.options = TdApi.MessageSendOptions().apply { sendingId = send.sendingId }
        }
        var dispatched = false
        return try {
            val future = client.sendMessage(request, true)
            outgoingFiles.track(listOf(retained), future)
            dispatched = true
            val message = future.get(VOICE_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (send != null) {
                automatedSends.completed(send, message)
            }
            message.id.toString()
        } catch (exception: ExecutionException) {
            if (send != null) {
                automatedSends.rejected(send)
            }
            throw TelegramSendFailedException(exception)
        } catch (exception: TimeoutException) {
            throw TelegramSendFailedException(exception)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw TelegramSendFailedException(exception)
        } finally {
            if (!dispatched) {
                outgoingFiles.completed(retained)
                if (send != null) {
                    automatedSends.rejected(send)
                }
            }
        }
    }
}
