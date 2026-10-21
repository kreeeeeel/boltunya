package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import ru.aigram.domain.chat.MessageDeletionOptions
import ru.aigram.domain.chat.MessageDeletionResult
import ru.aigram.domain.chat.MessageDeletionScope
import ru.aigram.exception.impl.TelegramDeleteFailedException
import ru.aigram.storage.MessageArchiveStore
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private const val MESSAGE_DELETION_TIMEOUT_SECONDS = 15L

@Component
class TelegramMessageDeletion(
    private val gateway: ObjectProvider<TdLightGateway>,
    private val view: TelegramMessageView,
    private val archive: MessageArchiveStore,
    private val history: TelegramHistoryTracker,
    private val policy: TelegramMessageDeletionPolicy,
) {
    fun options(chatId: String, messageId: String): MessageDeletionOptions {
        val service = gateway.getObject()
        val request = policy.propertiesRequest(service.parseChatId(chatId), messageId)
        return execute {
            policy.options(service.readyClient().send(request).get(MESSAGE_DELETION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        }
    }

    fun delete(chatId: String, messageId: String, scope: MessageDeletionScope): MessageDeletionResult {
        val service = gateway.getObject()
        val id = service.parseChatId(chatId)
        val request = policy.propertiesRequest(id, messageId)
        val client = service.readyClient()
        val account = service.status().userId
        return execute {
            val permissions = policy.options(
                client.send(request).get(MESSAGE_DELETION_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            val deletion = policy.deletionRequest(id, messageId, scope, permissions)
            val version = archive.version(account, chatId)
            val original = client.send(TdApi.GetMessage(id, request.messageId))
                .get(MESSAGE_DELETION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (service.readyClient() !== client || service.status().userId != account) {
                throw TelegramDeleteFailedException()
            }
            // Retain the exact message before Telegram can emit the permanent-delete update.
            archive.recordPage(account, chatId, listOf(view.map(original)), version)
            if (service.readyClient() !== client || service.status().userId != account) {
                throw TelegramDeleteFailedException()
            }
            client.send(deletion).get(MESSAGE_DELETION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            history.deleted(account, id, listOf(request.messageId))
            val retained = archive.find(account, chatId, messageId)
            if (retained == null || !retained.deleted) {
                throw TelegramDeleteFailedException()
            }
            MessageDeletionResult(chatId, messageId, retained.deletedAt)
        }
    }

    private fun <T> execute(action: () -> T): T = try {
        action()
    } catch (_: ExecutionException) {
        throw TelegramDeleteFailedException()
    } catch (_: TimeoutException) {
        throw TelegramDeleteFailedException()
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        throw TelegramDeleteFailedException()
    }
}
