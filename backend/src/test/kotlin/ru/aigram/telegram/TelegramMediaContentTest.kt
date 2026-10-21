package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.junit.jupiter.api.Test
import ru.aigram.domain.OutgoingMedia
import ru.aigram.domain.OutgoingMediaKind
import ru.aigram.exception.impl.InvalidUploadException
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TelegramMediaContentTest {
    private val builder = TelegramMediaContent()

    @Test
    fun `maps photos videos and original documents to current TDLib input shapes`() {
        val photo = assertIs<TdApi.InputMessagePhoto>(builder.content(media(OutgoingMediaKind.PHOTO)))
        assertIs<TdApi.InputFileLocal>(photo.photo.photo)
        assertEquals("caption", photo.caption.text)
        val video = assertIs<TdApi.InputMessageVideo>(builder.content(media(OutgoingMediaKind.VIDEO)))
        assertIs<TdApi.InputFileLocal>(video.video.video)
        assertTrue(video.video.supportsStreaming)
        val document = assertIs<TdApi.InputMessageDocument>(builder.content(media(OutgoingMediaKind.DOCUMENT)))
        assertTrue(document.document.disableContentTypeDetection)
    }

    @Test
    fun `preserves reply and forum topic in single messages and albums`() {
        val single = assertIs<TdApi.SendMessage>(builder.request(42, listOf(media(OutgoingMediaKind.PHOTO)), "123", 7))
        assertEquals(123L, assertIs<TdApi.InputMessageReplyToMessage>(single.replyTo).messageId)
        assertEquals(7, assertIs<TdApi.MessageTopicForum>(single.topicId).forumTopicId)
        val album =
            assertIs<TdApi.SendMessageAlbum>(
                builder.request(42, listOf(media(OutgoingMediaKind.PHOTO), media(OutgoingMediaKind.VIDEO)), "123", 7),
            )
        assertEquals(2, album.inputMessageContents.size)
        assertEquals(42L, album.chatId)
        assertEquals(123L, assertIs<TdApi.InputMessageReplyToMessage>(album.replyTo).messageId)
    }

    @Test
    fun `rejects invalid albums oversized captions and mismatched document groups`() {
        assertFailsWith<InvalidUploadException> { builder.request(42, emptyList(), "", 0) }
        assertFailsWith<InvalidUploadException> {
            builder.request(42, List(11) { media(OutgoingMediaKind.PHOTO) }, "", 0)
        }
        assertFailsWith<InvalidUploadException> {
            builder.request(42, listOf(media(OutgoingMediaKind.PHOTO).copy(caption = "x".repeat(1025))), "", 0)
        }
        assertFailsWith<InvalidUploadException> {
            builder.request(42, listOf(media(OutgoingMediaKind.PHOTO), media(OutgoingMediaKind.DOCUMENT)), "", 0)
        }
    }

    private fun media(kind: OutgoingMediaKind) = OutgoingMedia(Path.of("trusted-upload"), kind, "caption")
}
