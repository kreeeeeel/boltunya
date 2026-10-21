package ru.boltunya.telegram

import it.tdlight.jni.TdApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.storage.SettingsStore
import java.nio.file.Path
import java.time.Clock
import kotlin.test.assertEquals

class TelegramChatDirectoryTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `uses active usernames and cached basic and supergroup participant counts`() {
        val cache = TelegramCache()
        val files = TelegramMediaStore(directory) { error("No media expected") }
        val chats =
            TelegramChatDirectory(
                cache,
                files,
                TelegramMessageMapper(files),
                TelegramWallpaperMapper(files),
                SettingsStore(directory),
                TelegramTypingStore(cache, Clock.systemUTC()),
            )
        cache.users[10] = TdApi.User().apply {
            id = 10
            usernames = TdApi.Usernames().apply { activeUsernames = arrayOf("first_handle", "second_handle") }
        }
        val types = listOf(TdApi.ChatTypePrivate(10), TdApi.ChatTypeBasicGroup(20), TdApi.ChatTypeSupergroup(30, false))
        types.stream().forEach { type ->
            val id = when (type) {
                is TdApi.ChatTypePrivate -> 10L
                is TdApi.ChatTypeBasicGroup -> 20L
                else -> 30L
            }
            cache.chats[id] = TdApi.Chat().apply {
                this.id = id
                title = "Chat"
                this.type = type
                positions = arrayOf(position(TdApi.ChatListMain(), 10))
            }
        }
        cache.basicGroups[20] = TdApi.BasicGroup().apply { memberCount = 12 }
        cache.users[10]?.firstName = "Анна"
        cache.chats[10]?.lastMessage =
            TdApi.Message().apply {
                isOutgoing = true
                senderId = TdApi.MessageSenderUser(10)
                content =
                    TdApi.MessageText().apply { text = TdApi.FormattedText("Привет", emptyArray()) }
            }
        cache.chats[20]?.lastMessage =
            TdApi.Message().apply {
                senderId = TdApi.MessageSenderUser(10)
                content =
                    TdApi.MessageText().apply { text = TdApi.FormattedText("Привет", emptyArray()) }
            }
        cache.supergroups[30] = TdApi.Supergroup().apply { memberCount = 12345 }
        cache.lightBackground = TdApi.Background().apply {
            type = TdApi.BackgroundTypeFill(TdApi.BackgroundFillSolid(0xabcdef))
        }
        cache.darkBackground = TdApi.Background().apply {
            type = TdApi.BackgroundTypeFill(TdApi.BackgroundFillSolid(0x123456))
        }
        val result = chats.list().associateBy { it.id }
        assertEquals(listOf("#abcdef"), result["10"]?.wallpaper?.colors)
        assertEquals(listOf("#123456"), result["10"]?.darkWallpaper?.colors)
        assertEquals("first_handle", result["10"]?.username)
        assertEquals(true, result["10"]?.lastMessageOutgoing)
        assertEquals(false, result["20"]?.lastMessageOutgoing)
        assertEquals("Анна", result["20"]?.lastMessageSender)
        assertEquals(null, result["10"]?.memberCount)
        assertEquals(12, result["20"]?.memberCount)
        assertEquals(12345, result["30"]?.memberCount)
        cache.users[10]?.usernames?.activeUsernames = emptyArray()
        cache.supergroups[30]?.memberCount = 0
        assertEquals("", chats.list().single { it.id == "10" }.username)
        assertEquals(null, chats.list().single { it.id == "30" }.memberCount)
        cache.clear()
        assertEquals(null, cache.lightBackground)
        assertEquals(null, cache.darkBackground)
    }

    @Test
    fun `excludes cached public groups outside the main and archive lists`() {
        val cache = TelegramCache()
        val files = TelegramMediaStore(directory) { error("No media expected") }
        val chats =
            TelegramChatDirectory(
                cache,
                files,
                TelegramMessageMapper(files),
                TelegramWallpaperMapper(files),
                SettingsStore(directory),
                TelegramTypingStore(cache, Clock.systemUTC()),
            )
        listOf(1L, 2L, 3L).stream().forEach { id ->
            cache.chats[id] = TdApi.Chat().apply {
                this.id = id
                title = "Group"
                type = TdApi.ChatTypeSupergroup().apply { supergroupId = id }
                positions = emptyArray()
            }
        }
        cache.updatePosition(1, position(TdApi.ChatListMain(), 10))
        cache.updatePosition(2, position(TdApi.ChatListArchive(), 20))
        cache.supergroups[1] = TdApi.Supergroup().apply {
            id = 1
            status = TdApi.ChatMemberStatusLeft()
        }
        cache.supergroups[2] = TdApi.Supergroup().apply {
            id = 2
            status = TdApi.ChatMemberStatusMember()
        }
        assertEquals(setOf("1", "2"), chats.list().map { it.id }.toSet())
        assertEquals(false, chats.list().single { it.id == "1" }.joined)
        assertEquals(true, chats.list().single { it.id == "2" }.joined)
        cache.updatePosition(1, position(TdApi.ChatListMain(), 0))
        assertEquals(listOf("2"), chats.list().map { it.id })
        cache.updatePosition(2, position(TdApi.ChatListMain(), 30))
        cache.updatePosition(2, position(TdApi.ChatListArchive(), 0))
        assertEquals(listOf("2"), chats.list().map { it.id })
        assertEquals(1, cache.chats[2]?.positions?.size)
    }

    private fun position(list: TdApi.ChatList, order: Long) = TdApi.ChatPosition().apply {
        this.list = list
        this.order = order
    }
}
