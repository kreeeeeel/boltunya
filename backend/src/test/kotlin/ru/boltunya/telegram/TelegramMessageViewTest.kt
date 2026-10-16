package ru.boltunya.telegram

import it.tdlight.jni.TdApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals

class TelegramMessageViewTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `maps group author profile photos without fetching media during history mapping`() {
        val cache = TelegramCache()
        val files = TelegramMediaStore(directory) { error("Mapping must not download") }
        val view = TelegramMessageView(cache, TelegramMessageMapper(files), files)
        cache.chats[100] = TdApi.Chat().apply { type = TdApi.ChatTypeBasicGroup(100) }
        cache.users[10] = TdApi.User().apply {
            firstName = "Anna"
            lastName = "Miller"
            profilePhoto = TdApi.ProfilePhoto().apply { small = TdApi.File().apply { id = 21 } }
        }
        val mapped = view.map(message(TdApi.MessageSenderUser(10)))
        assertEquals(true, mapped.group)
        assertEquals("Anna Miller", mapped.senderName)
        assertEquals("user:10", mapped.senderId)
        assertEquals("/api/media/21", mapped.senderAvatarUrl)
        cache.users[10]?.profilePhoto = null
        assertEquals("", view.map(message(TdApi.MessageSenderUser(10))).senderAvatarUrl)
    }

    @Test
    fun `maps anonymous and channel authors from their chat photos`() {
        val cache = TelegramCache()
        val files = TelegramMediaStore(directory) { error("Mapping must not download") }
        val view = TelegramMessageView(cache, TelegramMessageMapper(files), files)
        cache.chats[100] = TdApi.Chat().apply { type = TdApi.ChatTypeSupergroup(100, false) }
        cache.chats[200] = TdApi.Chat().apply {
            title = "Team"
            photo = TdApi.ChatPhotoInfo().apply { small = TdApi.File().apply { id = 22 } }
        }
        val mapped = view.map(message(TdApi.MessageSenderChat(200)))
        assertEquals("Team", mapped.senderName)
        assertEquals("chat:200", mapped.senderId)
        assertEquals("/api/media/22", mapped.senderAvatarUrl)
    }

    private fun message(sender: TdApi.MessageSender) = TdApi.Message().apply {
        id = 1
        chatId = 100
        senderId = sender
        content = TdApi.MessageText().apply { text = TdApi.FormattedText("Hello", emptyArray()) }
    }
}
