package ru.aigram.transformer.chat

import org.junit.jupiter.api.Test
import ru.aigram.contract.dto.ChatKind
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.chat.ChatSummary
import ru.aigram.domain.chat.ChatTypingUser
import ru.aigram.domain.chat.ChatWallpaper
import ru.aigram.domain.chat.TypingAction
import ru.aigram.transformer.media.MediaTransformer
import kotlin.test.assertEquals
import kotlin.test.assertNull
import ru.aigram.contract.dto.TypingAction as ContractTypingAction

class ChatTransformerTest {
    private val transformer = ChatTransformer(ChatRuleTransformer(), MessageTransformer(MediaTransformer()))

    @Test
    fun `preserves group identity typing wallpaper and nullable membership information`() {
        val chat = ChatSummary(
            id = "-1009007199254740993",
            title = "Group",
            preview = "Photo",
            date = 4_000_000_000L,
            unread = 3,
            rule = ChatRule(),
            avatarUrl = "/api/media/14",
            kind = "supergroup",
            wallpaper = ChatWallpaper("wallpaper", "/api/media/15"),
            darkWallpaper = ChatWallpaper("fill", colors = listOf("#102030")),
            lastMessageId = "9007199254740993",
            username = "group_name",
            memberCount = 81,
            lastMessageOutgoing = true,
            lastMessageSender = "Alice",
            typingUsers = listOf(ChatTypingUser("9007199254740994", "Bob", TypingAction.RECORD_VOICE)),
        )

        val result = transformer.toContract(chat)

        assertEquals(chat.id, result.id)
        assertEquals(chat.date, result.date)
        assertEquals(ChatKind.SUPERGROUP, result.kind)
        assertEquals(chat.lastMessageId, result.lastMessageId)
        assertEquals(chat.lastMessageSender, result.lastMessageSender)
        assertEquals(chat.lastMessageOutgoing, result.lastMessageOutgoing)
        assertEquals(chat.memberCount, result.memberCount)
        assertEquals(chat.username, result.username)
        assertEquals(chat.wallpaper?.url, result.wallpaper?.url)
        assertEquals(chat.darkWallpaper?.colors, result.darkWallpaper?.colors)
        assertEquals(ContractTypingAction.RECORD_VOICE, result.typingUsers?.single()?.action)
        assertEquals(chat.typingUsers.single().id, result.typingUsers?.single()?.id)
        assertNull(result.joined)
    }
}
