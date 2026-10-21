package ru.aigram.telegram

import it.tdlight.jni.TdApi
import jakarta.inject.Inject
import org.springframework.stereotype.Component
import ru.aigram.domain.ChatMessage

@Component
class TelegramMessageView @Inject constructor(
    private val cache: TelegramCache,
    private val mapper: TelegramMessageMapper,
    private val files: TelegramMediaStore,
) {
    fun map(message: TdApi.Message): ChatMessage {
        val chat = cache.chats[message.chatId]
        val sender = (message.senderId as? TdApi.MessageSenderUser)?.userId
        val user = sender?.let(cache.users::get)
        val senderChat = (message.senderId as? TdApi.MessageSenderChat)?.chatId?.let(cache.chats::get)
        val avatar = user?.profilePhoto?.small ?: senderChat?.photo?.small
        val ownNames = cache.users[cache.ownId]?.usernames?.activeUsernames.orEmpty().map {
            "@" + it.lowercase()
        }.toSet()
        val mapped = mapper.map(message)
        val mention = message.containsUnreadMention || mapped.entities.stream().anyMatch { entity ->
            entity.type == "mention" &&
                mapped.text.substring(entity.offset, entity.offset + entity.length).lowercase() in ownNames ||
                entity.type == "mentionname" && entity.url == "tg://user?id=${cache.ownId}"
        }
        val reply = message.replyTo as? TdApi.MessageReplyToMessage
        return mapped.copy(
            group = chat?.type is TdApi.ChatTypeBasicGroup || chat?.type is TdApi.ChatTypeSupergroup,
            mentionsMe = mention,
            topicId = (message.topicId as? TdApi.MessageTopicForum)?.forumTopicId ?: 0,
            senderName =
            user?.let { (it.firstName.orEmpty() + " " + it.lastName.orEmpty()).trim() }
                ?: senderChat?.title.orEmpty(),
            senderAvatarUrl = avatar?.let {
                "/api/media/${files.register(it, "image/jpeg", "sender-avatar.jpg")}"
            }.orEmpty(),
            senderId = when (val origin = message.senderId) {
                is TdApi.MessageSenderUser -> "user:${origin.userId}"
                is TdApi.MessageSenderChat -> "chat:${origin.chatId}"
                else -> ""
            },
            senderUsername = user?.usernames?.activeUsernames?.firstOrNull().orEmpty(),
            replyToId = reply?.messageId?.toString().orEmpty(),
            replyText = (reply?.content as? TdApi.MessageText)?.text?.text.orEmpty(),
            read = message.isOutgoing && message.id <= (chat?.lastReadOutboxMessageId ?: 0),
        )
    }
}
