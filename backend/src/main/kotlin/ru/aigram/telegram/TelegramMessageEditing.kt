package ru.aigram.telegram

import it.tdlight.client.SimpleTelegramClient
import it.tdlight.client.TelegramError
import it.tdlight.jni.TdApi
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.MessageEditKind
import ru.aigram.domain.chat.MessageEditOptions
import ru.aigram.domain.chat.MessageEditRequest
import ru.aigram.exception.impl.MessageEditConflictException
import ru.aigram.exception.impl.TelegramEditFailedException
import ru.aigram.exception.impl.TelegramNotConnectedException
import ru.aigram.exception.impl.TelegramRateLimitException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private const val EDIT_TIMEOUT_SECONDS = 15L
private const val EDIT_LOCK_STRIPES = 64
private const val RATE_LIMIT_STATUS = 429
private const val EDIT_RATE_LIMIT_SECONDS = 60L

@Component
class TelegramMessageEditing(
    private val gateway: ObjectProvider<TdLightGateway>,
    private val connection: TelegramConnectionState,
    private val view: TelegramMessageView,
    private val history: TelegramHistoryTracker,
    private val policy: TelegramMessageEditPolicy,
) {
    private val locks = Array(EDIT_LOCK_STRIPES) { Any() }

    fun options(chatId: String, messageId: String): MessageEditOptions = execute {
        val service = gateway.getObject()
        val chat = service.parseChatId(chatId)
        val id = policy.messageId(messageId)
        val client = service.readyClient()
        val account = service.status().userId
        val version = connection.version
        fun guard() = guard(service, client, account, version)

        guard()
        val message = client.send(TdApi.GetMessage(chat, id)).get(EDIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        guard()
        val permissions = client.send(TdApi.GetMessageProperties(chat, id)).get(EDIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        guard()
        policy.verifyOwner(message, permissions, account)
        val maximum = maximum(client, message)
        guard()
        policy.options(message, account, version, maximum)
    }

    fun edit(chatId: String, messageId: String, request: MessageEditRequest): ChatMessage {
        val stripe = Math.floorMod((chatId + ":" + messageId).hashCode(), locks.size)
        return synchronized(locks[stripe]) {
            execute { save(chatId, messageId, request) }
        }
    }

    private fun save(chatId: String, messageId: String, request: MessageEditRequest): ChatMessage {
        val service = gateway.getObject()
        val chat = service.parseChatId(chatId)
        val id = policy.messageId(messageId)
        val client = service.readyClient()
        val account = service.status().userId
        val version = connection.version
        fun guard() = guard(service, client, account, version)

        guard()
        val message = client.send(TdApi.GetMessage(chat, id)).get(EDIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        guard()
        val permissions = client.send(TdApi.GetMessageProperties(chat, id)).get(EDIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        guard()
        policy.verifyOwner(message, permissions, account)
        if (policy.revision(message, account, version) != request.revision) {
            throw MessageEditConflictException()
        }
        val maximum = maximum(client, message)
        guard()
        val text = policy.updated(message, request, maximum)
        if (policy.formatted(message) == text) {
            guard()
            return history.record(view.map(message))
        }

        guard()
        val updated = client.send(policy.request(message, text)).get(EDIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        guard()
        return history.edited(account, updated, view.map(updated))
    }

    private fun maximum(client: SimpleTelegramClient, message: TdApi.Message): Int {
        val name = when (policy.kind(message)) {
            MessageEditKind.TEXT -> "message_text_length_max"
            MessageEditKind.CAPTION -> "message_caption_length_max"
        }
        return policy.limit(client.send(TdApi.GetOption(name)).get(EDIT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
    }

    private fun guard(service: TdLightGateway, client: SimpleTelegramClient, account: String, version: Long) {
        val sameClient = try {
            service.readyClient() === client
        } catch (_: TelegramNotConnectedException) {
            false
        }
        policy.verifyConnection(account, version, service.status().userId, connection.version, sameClient)
    }

    private fun <T> execute(action: () -> T): T = try {
        action()
    } catch (exception: ExecutionException) {
        val cause = exception.cause
        if (cause is TelegramError && cause.errorCode == RATE_LIMIT_STATUS) {
            throw TelegramRateLimitException(EDIT_RATE_LIMIT_SECONDS, cause)
        }
        throw TelegramEditFailedException(exception)
    } catch (exception: TimeoutException) {
        throw TelegramEditFailedException(exception)
    } catch (exception: InterruptedException) {
        Thread.currentThread().interrupt()
        throw TelegramEditFailedException(exception)
    }
}
