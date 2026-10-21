package ru.aigram.transformer.chat

import org.springframework.stereotype.Component
import ru.aigram.contract.dto.Chat
import ru.aigram.contract.dto.ChatKind
import ru.aigram.contract.dto.WallpaperKind
import ru.aigram.domain.chat.ChatSummary
import ru.aigram.domain.chat.ChatTypingUser
import ru.aigram.domain.chat.ChatWallpaper
import ru.aigram.domain.media.ChatGallery
import ru.aigram.contract.dto.ChatGallery as ContractChatGallery
import ru.aigram.contract.dto.ChatTypingUser as ContractChatTypingUser
import ru.aigram.contract.dto.ChatWallpaper as ContractChatWallpaper
import ru.aigram.contract.dto.TypingAction as ContractTypingAction

@Component
class ChatTransformer(private val rules: ChatRuleTransformer, private val messages: MessageTransformer) {
    fun toContract(value: ChatSummary): Chat = Chat(
        id = value.id,
        title = value.title,
        preview = value.preview,
        date = value.date,
        unread = value.unread,
        rule = rules.toContract(value.rule),
        avatarUrl = value.avatarUrl,
        kind = ChatKind.forValue(value.kind),
        wallpaper = value.wallpaper?.let(::toContract),
        darkWallpaper = value.darkWallpaper?.let(::toContract),
        lastMessageId = value.lastMessageId,
        joined = value.joined,
        username = value.username,
        memberCount = value.memberCount,
        lastMessageOutgoing = value.lastMessageOutgoing,
        lastMessageSender = value.lastMessageSender,
        typingUsers = value.typingUsers.stream().map(::toContract).toList(),
    )

    fun toContract(value: ChatGallery): ContractChatGallery = ContractChatGallery(
        messages = value.messages.stream().map(messages::toContract).toList(),
        hasMoreBefore = value.hasMoreBefore,
        hasMoreAfter = value.hasMoreAfter,
    )

    private fun toContract(value: ChatWallpaper): ContractChatWallpaper = ContractChatWallpaper(
        kind = WallpaperKind.forValue(value.kind),
        url = value.url,
        colors = value.colors,
        rotation = value.rotation,
        blurred = value.blurred,
        dimming = value.dimming,
        intensity = value.intensity,
    )

    private fun toContract(value: ChatTypingUser): ContractChatTypingUser = ContractChatTypingUser(
        id = value.id,
        name = value.name,
        action = ContractTypingAction.valueOf(value.action.name),
    )
}
