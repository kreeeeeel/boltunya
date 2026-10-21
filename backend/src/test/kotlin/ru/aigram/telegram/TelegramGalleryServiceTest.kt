package ru.aigram.telegram

import it.tdlight.jni.TdApi
import jakarta.inject.Provider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.exception.impl.MediaUnavailableException
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TelegramGalleryServiceTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `searches bounded media around the current message without walking the chat history`() {
        val request = service().search(42, 100)
        assertEquals(42L, request.chatId)
        assertEquals(100L, request.fromMessageId)
        assertEquals(-30, request.offset)
        assertEquals(60, request.limit)
        assertIs<TdApi.SearchMessagesFilterPhotoAndVideo>(request.filter)
    }

    @Test
    fun `merges selected media and adjacent photos once in chronological order`() {
        val result = service().combine(
            42,
            TdApi.FoundChatMessages(50, arrayOf(photo(200), photo(100), photo(50).apply { chatId = 43 }), 10),
            photo(100),
            TdApi.FoundChatMessages(50, arrayOf(photo(300)), 0),
        )
        assertEquals(listOf("100", "200"), result.messages.map { it.id })
        assertTrue(result.hasMoreBefore)
        assertTrue(result.hasMoreAfter)
        assertFailsWith<MediaUnavailableException> {
            service().combine(
                42,
                TdApi.FoundChatMessages(0, emptyArray(), 0),
                photo(100).apply {
                    chatId = 43
                },
                TdApi.FoundChatMessages(0, emptyArray(), 0),
            )
        }
    }

    private fun service(): TelegramGalleryService {
        val files = TelegramMediaStore(directory) { error("No downloads in gallery tests") }
        val view = TelegramMessageView(TelegramCache(), TelegramMessageMapper(files), files)
        return TelegramGalleryService(Provider { error("No Telegram in gallery tests") }, view)
    }

    private fun photo(messageId: Long) = TdApi.Message().apply {
        id = messageId
        chatId = 42
        content = TdApi.MessagePhoto().apply {
            photo = TdApi.Photo().apply {
                sizes =
                    arrayOf(TdApi.PhotoSize("x", TdApi.File().apply { id = messageId.toInt() }, 100, 100, intArrayOf()))
            }
            caption = TdApi.FormattedText("", emptyArray())
        }
    }
}
