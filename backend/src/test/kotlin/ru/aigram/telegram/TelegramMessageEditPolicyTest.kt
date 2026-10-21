package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.junit.jupiter.api.Test
import ru.aigram.domain.chat.MessageEditRequest
import ru.aigram.exception.impl.InvalidHistoryIdentifierException
import ru.aigram.exception.impl.InvalidMessageEditException
import ru.aigram.exception.impl.MessageEditConflictException
import ru.aigram.exception.impl.MessageEditNotAllowedException
import ru.aigram.exception.impl.MessageEditTooLongException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TelegramMessageEditPolicyTest {
    private val policy = TelegramMessageEditPolicy(TelegramMessageEditEntities())

    @Test
    fun `rechecks exact identifiers outgoing ownership permissions and connection identity`() {
        val original = message(TdApi.MessageText(TdApi.FormattedText("Original", emptyArray()), null, null))
        val permissions = TdApi.MessageProperties().apply { canBeEdited = true }
        policy.verifyOwner(original, permissions, "100")
        assertEquals(9007199254740993L, policy.messageId("9007199254740993"))
        listOf("0", "-1", "01", "+1", " 1", "999999999999999999999").stream().forEach {
            assertFailsWith<InvalidHistoryIdentifierException> { policy.messageId(it) }
        }
        original.isOutgoing = false
        assertFailsWith<MessageEditNotAllowedException> { policy.verifyOwner(original, permissions, "100") }
        original.isOutgoing = true
        assertFailsWith<MessageEditNotAllowedException> { policy.verifyOwner(original, permissions, "101") }
        permissions.canBeEdited = false
        assertFailsWith<MessageEditNotAllowedException> { policy.verifyOwner(original, permissions, "100") }
        policy.verifyConnection("100", 3, "100", 3, true)
        assertFailsWith<MessageEditConflictException> { policy.verifyConnection("100", 3, "101", 3, true) }
        assertFailsWith<MessageEditConflictException> { policy.verifyConnection("100", 3, "100", 4, true) }
        assertFailsWith<MessageEditConflictException> { policy.verifyConnection("100", 3, "100", 3, false) }
    }

    @Test
    fun `revision includes hidden entity metadata preview account and edit version`() {
        val preview = TdApi.LinkPreviewOptions(false, "https://example.org", true, false, true)
        val entity = TdApi.TextEntity(0, 2, TdApi.TextEntityTypeCustomEmoji(9007199254740993L))
        val original = message(TdApi.MessageText(TdApi.FormattedText("😀", arrayOf(entity)), null, preview))
        val revision = policy.revision(original, "100", 3)

        assertEquals(64, revision.length)
        assertEquals(revision, policy.revision(original, "100", 3))
        assertNotEquals(revision, policy.revision(original, "101", 3))
        assertNotEquals(revision, policy.revision(original, "100", 4))
        entity.type = TdApi.TextEntityTypeCustomEmoji(9007199254740994L)
        assertNotEquals(revision, policy.revision(original, "100", 3))
        entity.type = TdApi.TextEntityTypeCustomEmoji(9007199254740993L)
        preview.isDisabled = true
        assertNotEquals(revision, policy.revision(original, "100", 3))
        preview.isDisabled = false
        original.editDate = 42
        assertNotEquals(revision, policy.revision(original, "100", 3))
    }

    @Test
    fun `text editing preserves exact whitespace link preview options and user draft`() {
        val preview = TdApi.LinkPreviewOptions(true, "https://example.org", false, true, true)
        val original = message(TdApi.MessageText(TdApi.FormattedText("Original", emptyArray()), null, preview))
        val text = policy.updated(original, MessageEditRequest("  New **text**\n", emptyList(), "0".repeat(64)), 32)
        val request = policy.request(original, text) as TdApi.EditMessageText
        val content = request.inputMessageContent as TdApi.InputMessageText

        assertEquals(original.id, request.messageId)
        assertEquals(original.chatId, request.chatId)
        assertEquals("  New **text**\n", content.text.text)
        assertSame(preview, content.linkPreviewOptions)
        assertFalse(content.clearDraft)
        assertEquals(null, request.replyMarkup)
        assertFailsWith<InvalidMessageEditException> {
            policy.updated(original, MessageEditRequest("  ", emptyList(), "0".repeat(64)), 32)
        }
    }

    @Test
    fun `captions preserve placement permit removal and count astral emoji as unicode code points`() {
        val photo = message(
            TdApi.MessagePhoto().apply {
                caption = TdApi.FormattedText("Caption", emptyArray())
                showCaptionAboveMedia = true
            },
        )
        val empty = policy.updated(photo, MessageEditRequest("", emptyList(), "0".repeat(64)), 2)
        val edit = policy.request(photo, empty) as TdApi.EditMessageCaption
        assertTrue(edit.showCaptionAboveMedia)
        assertEquals("", edit.caption.text)
        assertEquals(2, policy.limit(TdApi.OptionValueInteger(2)))
        assertEquals("😀😀", policy.updated(photo, MessageEditRequest("😀😀", emptyList(), "0".repeat(64)), 2).text)
        assertFailsWith<MessageEditTooLongException> {
            policy.updated(photo, MessageEditRequest("😀😀a", emptyList(), "0".repeat(64)), 2)
        }
        val video = message(
            TdApi.MessageVideo().apply {
                caption = TdApi.FormattedText("", emptyArray())
                showCaptionAboveMedia = false
            },
        )
        assertFalse((policy.request(video, empty) as TdApi.EditMessageCaption).showCaptionAboveMedia)
    }

    @Test
    fun `caption revision tracks media identity and placement while ignoring file download progress`() {
        val file = TdApi.File().apply { id = 7 }
        val photo = TdApi.MessagePhoto().apply {
            caption = TdApi.FormattedText("Caption", emptyArray())
            this.photo = TdApi.Photo().apply {
                sizes = arrayOf(TdApi.PhotoSize("x", file, 100, 100, intArrayOf()))
            }
        }
        val original = message(photo)
        val revision = policy.revision(original, "100", 1)

        file.size = 1000
        file.expectedSize = 1000
        assertEquals(revision, policy.revision(original, "100", 1))
        photo.showCaptionAboveMedia = true
        assertNotEquals(revision, policy.revision(original, "100", 1))
        photo.showCaptionAboveMedia = false
        file.id = 8
        assertNotEquals(revision, policy.revision(original, "100", 1))
    }

    private fun message(content: TdApi.MessageContent): TdApi.Message = TdApi.Message().apply {
        id = 9007199254740993L
        chatId = -1009007199254740993L
        senderId = TdApi.MessageSenderUser(100)
        isOutgoing = true
        this.content = content
    }
}
