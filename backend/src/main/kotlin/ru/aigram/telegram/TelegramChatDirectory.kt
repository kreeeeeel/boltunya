package ru.aigram.telegram

import it.tdlight.jni.TdApi
import jakarta.inject.Inject
import ru.aigram.domain.ChatSummary
import ru.aigram.storage.SettingsStore

class TelegramChatDirectory @Inject constructor(
    private val cache: TelegramCache,
    private val files: TelegramMediaStore,
    private val mapper: TelegramMessageMapper,
    private val wallpaper: TelegramWallpaperMapper,
    private val settings: SettingsStore,
    private val typing: TelegramTypingStore,
) {
    fun list(): List<ChatSummary> = cache.chats.values.stream().filter { chat ->
        val type = chat.type
        val listed = chat.positions.orEmpty().any {
            it.order != 0L && (it.list is TdApi.ChatListMain || it.list is TdApi.ChatListArchive)
        }
        listed && (
            type is TdApi.ChatTypePrivate && type.userId != cache.ownId &&
                cache.users[type.userId]?.type !is TdApi.UserTypeBot ||
                type is TdApi.ChatTypeBasicGroup || type is TdApi.ChatTypeSupergroup && !type.isChannel
            )
    }.map { chat ->
        val avatar =
            chat.photo?.small
                ?: (chat.type as? TdApi.ChatTypePrivate)?.let { cache.users[it.userId]?.profilePhoto?.small }
        ChatSummary(
            id = chat.id.toString(), title = chat.title, preview = chat.lastMessage?.let(mapper::preview).orEmpty(),
            date =
            chat.lastMessage?.date?.toLong() ?: 0,
            unread = chat.unreadCount, rule = settings.rule(chat.id.toString()),
            avatarUrl = avatar?.let { "/api/media/${files.register(it, "image/jpeg", "avatar.jpg")}" }.orEmpty(),
            kind = when (chat.type) {
                is TdApi.ChatTypeBasicGroup -> "group"
                is TdApi.ChatTypeSupergroup -> "supergroup"
                else -> "private"
            },
            wallpaper = wallpaper.map(chat.background, cache.lightBackground),
            darkWallpaper = wallpaper.map(chat.background, cache.darkBackground ?: cache.lightBackground),
            lastMessageId = chat.lastMessage?.id?.toString().orEmpty(),
            joined = joined(chat.type),
            username = (chat.type as? TdApi.ChatTypePrivate)?.let {
                cache.users[it.userId]?.usernames?.activeUsernames?.firstOrNull()
            }.orEmpty(),
            memberCount = memberCount(chat.type),
            lastMessageOutgoing = chat.lastMessage?.isOutgoing == true,
            lastMessageSender = senderName(chat.lastMessage?.senderId),
            typingUsers = typing.list(chat.id),
        )
    }.sorted(compareByDescending { it.date }).toList()

    private fun senderName(sender: TdApi.MessageSender?): String = when (sender) {
        is TdApi.MessageSenderUser -> cache.users[sender.userId]?.let { user ->
            user.firstName.orEmpty().ifBlank { user.lastName.orEmpty() }
        }.orEmpty()
        is TdApi.MessageSenderChat -> cache.chats[sender.chatId]?.title.orEmpty()
        else -> ""
    }

    private fun memberCount(type: TdApi.ChatType): Int? = when (type) {
        is TdApi.ChatTypeBasicGroup -> cache.basicGroups[type.basicGroupId]?.memberCount
        is TdApi.ChatTypeSupergroup -> cache.supergroups[type.supergroupId]?.memberCount
        else -> null
    }?.takeIf { it > 0 }

    private fun joined(type: TdApi.ChatType): Boolean? {
        val status = when (type) {
            is TdApi.ChatTypeBasicGroup -> cache.basicGroups[type.basicGroupId]?.status
            is TdApi.ChatTypeSupergroup -> cache.supergroups[type.supergroupId]?.status
            else -> null
        }
        return when (status) {
            is TdApi.ChatMemberStatusMember, is TdApi.ChatMemberStatusAdministrator -> true
            is TdApi.ChatMemberStatusCreator -> status.isMember
            is TdApi.ChatMemberStatusRestricted -> status.isMember
            is TdApi.ChatMemberStatusLeft, is TdApi.ChatMemberStatusBanned -> false
            else -> null
        }
    }
}
