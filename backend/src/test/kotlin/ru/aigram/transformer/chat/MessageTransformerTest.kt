package ru.aigram.transformer.chat

import org.junit.jupiter.api.Test
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.MessageEntity
import ru.aigram.domain.media.MediaAttachment
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessageTransformerTest {

    @Test
    fun `preserves opaque identifiers long timestamps formatting and retained deletion metadata`() {
        val source = ChatMessage(
            id = "9007199254740993",
            chatId = "-1009007199254740993",
            text = "Caption",
            outgoing = true,
            date = 4_000_000_000L,
            kind = "photo",
            media = MediaAttachment(73, "image/jpeg", "photo.jpg", 800, 600, 74, 6_000_000_000L),
            entities = listOf(MessageEntity(0, 7, "textUrl", "https://example.org", "")),
            read = true,
            automated = true,
            group = true,
            mentionsMe = true,
            topicId = 17,
            senderName = "Alice",
            senderAvatarUrl = "/api/media/70",
            replyToId = "9007199254740992",
            replyText = "Previous message",
            senderId = "9007199254740994",
            deleted = true,
            deletedAt = 4_000_000_001L,
            contextExcluded = true,
            senderUsername = "alice",
            mediaAlbumId = "9007199254740995",
            editedAt = 4_000_000_002L,
        )

        val result = source.toContract()

        assertEquals(source.id, result.id)
        assertEquals(source.chatId, result.chatId)
        assertEquals(source.date, result.date)
        assertEquals(source.media?.size, result.media?.propertySize)
        assertEquals(source.entities.single().url, result.entities?.single()?.url)
        assertEquals(source.senderId, result.senderId)
        assertEquals(source.senderUsername, result.senderUsername)
        assertEquals(source.senderAvatarUrl, result.senderAvatarUrl)
        assertEquals(source.mediaAlbumId, result.mediaAlbumId)
        assertEquals(source.editedAt, result.editedAt)
        assertEquals(source.replyToId, result.replyToId)
        assertEquals(source.replyText, result.replyText)
        assertEquals(source.deletedAt, result.deletedAt)
        assertTrue(result.deleted == true)
        assertTrue(result.automated == true)
        assertTrue(result.contextExcluded == true)
        assertTrue(result.group == true)
        assertTrue(result.mentionsMe == true)
        assertEquals(17, result.topicId)
    }

    @Test
    fun `leaves absent media absent`() {
        assertNull(ChatMessage("1", "42", "Text", false, 0).toContract().media)
    }
}
