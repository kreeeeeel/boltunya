package ru.aigram.telegram

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
            usernames = TdApi.Usernames().apply { activeUsernames = arrayOf("anna_example", "another_name") }
        }
        val mapped = view.map(message(TdApi.MessageSenderUser(10)))
        assertEquals(true, mapped.group)
        assertEquals("Anna Miller", mapped.senderName)
        assertEquals("user:10", mapped.senderId)
        assertEquals("anna_example", mapped.senderUsername)
        assertEquals("/api/media/21", mapped.senderAvatarUrl)
        cache.users[10]?.profilePhoto = null
        cache.users[10]?.usernames = null
        assertEquals("", view.map(message(TdApi.MessageSenderUser(10))).senderAvatarUrl)
        assertEquals("", view.map(message(TdApi.MessageSenderUser(10))).senderUsername)
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

    @Test
    fun `keeps real forwarded source names and never invents a name for an unknown origin`() {
        val cache = TelegramCache()
        val files = TelegramMediaStore(directory) { error("Mapping must not download") }
        val view = TelegramMessageView(cache, TelegramMessageMapper(files), files)
        cache.users[20] = TdApi.User().apply { firstName = "Alice" }
        cache.chats[30] = TdApi.Chat().apply { title = "Source channel" }
        val forwarded = message(TdApi.MessageSenderUser(10)).apply {
            forwardInfo = TdApi.MessageForwardInfo().apply {
                origin = TdApi.MessageOriginUser().apply { senderUserId = 20 }
            }
        }

        assertEquals("Alice", view.map(forwarded).forwardedFrom)
        assertEquals(true, view.map(forwarded).forwarded)
        forwarded.forwardInfo.origin = TdApi.MessageOriginChannel().apply { chatId = 30 }
        assertEquals("Source channel", view.map(forwarded).forwardedFrom)
        forwarded.forwardInfo.origin = TdApi.MessageOriginHiddenUser().apply { senderName = "Hidden sender" }
        assertEquals("Hidden sender", view.map(forwarded).forwardedFrom)
        forwarded.forwardInfo.origin = TdApi.MessageOriginUser().apply { senderUserId = 999 }
        assertEquals("", view.map(forwarded).forwardedFrom)
        assertEquals(true, view.map(forwarded).forwarded)
    }

    @Test
    fun `only confirmed positive outgoing message ids can have a read receipt`() {
        val cache = TelegramCache()
        cache.chats[100] = TdApi.Chat().apply { lastReadOutboxMessageId = 10 }
        val files = TelegramMediaStore(directory) { error("Mapping must not download") }
        val view = TelegramMessageView(cache, TelegramMessageMapper(files), files)
        val outgoing = message(TdApi.MessageSenderUser(10)).apply { isOutgoing = true }

        assertEquals(true, view.map(outgoing).read)
        outgoing.sendingState = TdApi.MessageSendingStatePending()
        assertEquals(false, view.map(outgoing).read)
        outgoing.sendingState = null
        outgoing.id = -1
        assertEquals(false, view.map(outgoing).read)
        outgoing.id = 0
        assertEquals(false, view.map(outgoing).read)
        outgoing.id = 11
        assertEquals(false, view.map(outgoing).read)
    }

    private fun message(sender: TdApi.MessageSender) = TdApi.Message().apply {
        id = 1
        chatId = 100
        senderId = sender
        content = TdApi.MessageText().apply { text = TdApi.FormattedText("Hello", emptyArray()) }
    }
}
