package ru.aigram.transformer.chat

import ru.aigram.contract.dto.Chat
import ru.aigram.contract.dto.ChatKind
import ru.aigram.contract.dto.WallpaperKind
import ru.aigram.domain.chat.ChatSummary
import ru.aigram.domain.chat.ChatTypingUser
import ru.aigram.domain.chat.ChatWallpaper
import ru.aigram.contract.dto.ChatTypingUser as ContractChatTypingUser
import ru.aigram.contract.dto.ChatWallpaper as ContractChatWallpaper
import ru.aigram.contract.dto.TypingAction as ContractTypingAction

fun ChatSummary.toContract(): Chat = Chat(
    id = id,
    title = title,
    preview = preview,
    date = date,
    unread = unread,
    rule = rule.toContract(),
    avatarUrl = avatarUrl,
    kind = ChatKind.forValue(kind),
    wallpaper = wallpaper?.toContract(),
    darkWallpaper = darkWallpaper?.toContract(),
    lastMessageId = lastMessageId,
    joined = joined,
    username = username,
    memberCount = memberCount,
    lastMessageOutgoing = lastMessageOutgoing,
    lastMessageSender = lastMessageSender,
    typingUsers = typingUsers.stream().map { it.toContract() }.toList(),
    lastMessageRead = lastMessageRead,
)

private fun ChatWallpaper.toContract(): ContractChatWallpaper = ContractChatWallpaper(
    kind = WallpaperKind.forValue(kind),
    url = url,
    colors = colors,
    rotation = rotation,
    blurred = blurred,
    dimming = dimming,
    intensity = intensity,
)

private fun ChatTypingUser.toContract(): ContractChatTypingUser = ContractChatTypingUser(
    id = id,
    name = name,
    action = ContractTypingAction.valueOf(action.name),
)
