package ru.aigram.telegram

import it.tdlight.client.SimpleTelegramClientBuilder
import it.tdlight.jni.TdApi
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.automation.proactive.ProactiveCoordinator
import ru.aigram.domain.telegram.TelegramStatus
import ru.aigram.exception.impl.TelegramRequestRejectedException
import ru.aigram.exception.impl.TelegramUpdateFailedException
import ru.aigram.memory.MemoryCoordinator

@Component
class TelegramUpdateHandler(
    private val cache: TelegramCache,
    private val connection: TelegramConnectionState,
    private val messages: TelegramMessageView,
    private val replies: ObjectProvider<ReplyCoordinator>,
    private val memory: ObjectProvider<MemoryCoordinator>,
    private val history: TelegramHistoryTracker,
    private val media: TelegramMediaStore,
    private val prefetch: MediaPrefetcher,
    private val typing: TelegramTypingStore,
    private val delivery: TelegramMediaDeliveryTracker,
    private val initiatives: ObjectProvider<ProactiveCoordinator>,
) {
    fun register(builder: SimpleTelegramClientBuilder, version: Long) {
        builder.addDefaultExceptionHandler {
            if (connection.version != version) {
                return@addDefaultExceptionHandler
            }
            connection.status =
                connection.status.copy(error = TelegramRequestRejectedException().message)
        }
        builder.addUpdateExceptionHandler {
            if (connection.version != version) {
                return@addUpdateExceptionHandler
            }
            connection.status =
                connection.status.copy(error = TelegramUpdateFailedException().message)
        }
        builder.addUpdateHandler(TdApi.UpdateUser::class.java) {
            if (connection.version ==
                version
            ) {
                cache.users[it.user.id] = it.user
            }
        }
        builder.addUpdateHandler(TdApi.UpdateNewChat::class.java) {
            if (connection.version ==
                version
            ) {
                cache.chats[it.chat.id] = it.chat
            }
        }
        builder.addUpdateHandler(TdApi.UpdateBasicGroup::class.java) { update ->
            if (connection.version == version) {
                cache.basicGroups[update.basicGroup.id] = update.basicGroup
            }
        }
        builder.addUpdateHandler(TdApi.UpdateSupergroup::class.java) { update ->
            if (connection.version == version) {
                cache.supergroups[update.supergroup.id] = update.supergroup
            }
        }
        builder.addUpdateHandler(TdApi.UpdateChatBackground::class.java) { update ->
            if (connection.version != version) {
                return@addUpdateHandler
            }
            cache.updateChat(update.chatId) {
                it.background =
                    update.background
            }
        }
        builder.addUpdateHandler(TdApi.UpdateDefaultBackground::class.java) { update ->
            if (connection.version != version) {
                return@addUpdateHandler
            }
            if (update.forDarkTheme) {
                cache.darkBackground = update.background
            } else {
                cache.lightBackground = update.background
            }
        }
        builder.addUpdateHandler(TdApi.UpdateChatPhoto::class.java) { update ->
            if (connection.version != version) {
                return@addUpdateHandler
            }
            cache.updateChat(update.chatId) {
                it.photo =
                    update.photo
            }
        }
        builder.addUpdateHandler(TdApi.UpdateChatLastMessage::class.java) { update ->
            if (connection.version != version) {
                return@addUpdateHandler
            }
            cache.updateChat(update.chatId) {
                it.lastMessage =
                    update.lastMessage
                it.positions = update.positions
            }
        }
        builder.addUpdateHandler(TdApi.UpdateChatPosition::class.java) { update ->
            if (connection.version == version) {
                cache.updatePosition(update.chatId, update.position)
            }
        }
        builder.addUpdateHandler(TdApi.UpdateChatDraftMessage::class.java) { update ->
            if (connection.version == version) {
                cache.updateChat(update.chatId) { it.positions = update.positions }
            }
        }
        builder.addUpdateHandler(TdApi.UpdateChatReadInbox::class.java) { update ->
            if (connection.version != version) {
                return@addUpdateHandler
            }
            cache.updateChat(update.chatId) {
                it.unreadCount =
                    update.unreadCount
            }
        }
        builder.addUpdateHandler(TdApi.UpdateChatReadOutbox::class.java) { update ->
            if (connection.version != version) {
                return@addUpdateHandler
            }
            cache.updateChat(update.chatId) {
                it.lastReadOutboxMessageId =
                    update.lastReadOutboxMessageId
            }
        }
        builder.addUpdateHandler(TdApi.UpdateNewMessage::class.java) { update ->
            if (connection.version == version) {
                typing.clear(update.message.chatId, update.message.senderId)
                val mapped = messages.map(update.message)
                val message = try {
                    history.record(mapped)
                } catch (exception: Exception) {
                    System.err.println("Message archive failed: ${exception.javaClass.simpleName}")
                    mapped
                }
                if (message.deleted || update.message.date.toLong() < cache.startedAt) {
                    return@addUpdateHandler
                }
                prefetch.observe(message)
                try {
                    memory.getObject().observe(message)
                } catch (exception: Exception) {
                    System.err.println("Memory tracking failed: ${exception.javaClass.simpleName}")
                }
                initiatives.getObject().observe(message)
                replies.getObject().onMessage(message)
            }
        }
        builder.addUpdateHandler(TdApi.UpdateFile::class.java) {
            if (connection.version ==
                version
            ) {
                media.update(it.file)
            }
        }
        builder.addUpdateHandler(TdApi.UpdateMessageContent::class.java) {
            if (connection.version ==
                version
            ) {
                history.edited(it)
            }
        }
        builder.addUpdateHandler(TdApi.UpdateMessageEdited::class.java) {
            if (connection.version == version) {
                history.edited(it)
            }
        }
        builder.addUpdateHandler(TdApi.UpdateMessageSendSucceeded::class.java) {
            if (connection.version == version) {
                delivery.succeeded(cache.ownId.toString(), it.message.chatId, it.oldMessageId, it.message.id)
                history.record(messages.map(it.message))
            }
        }
        builder.addUpdateHandler(TdApi.UpdateMessageSendFailed::class.java) {
            if (connection.version ==
                version
            ) {
                delivery.failed(cache.ownId.toString(), it.message.chatId, it.oldMessageId)
            }
        }
        builder.addUpdateHandler(TdApi.UpdateChatAction::class.java) {
            if (connection.version == version) {
                typing.update(it.chatId, it.senderId, it.action)
            }
        }
        builder.addUpdateHandler(TdApi.UpdateDeleteMessages::class.java) { update ->
            if (connection.version != version) {
                return@addUpdateHandler
            }
            history.deleted(update)
        }
        builder.addUpdateHandler(TdApi.UpdateAuthorizationState::class.java) { update ->
            if (connection.version != version) {
                return@addUpdateHandler
            }
            when (update.authorizationState) {
                is TdApi.AuthorizationStateReady ->
                    connection.status =
                        connection.status.copy(state = "ready", error = "", prompt = "")
                is TdApi.AuthorizationStateClosed -> connection.status = TelegramStatus()
                else -> Unit
            }
        }
    }
}
