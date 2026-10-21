package ru.aigram.component.chat

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.automation.FakeTelegramGateway
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ContextExclusionUpdate
import ru.aigram.domain.media.ChatGallery
import ru.aigram.domain.media.MediaAttachment
import ru.aigram.domain.telegram.TelegramStatus
import ru.aigram.exception.impl.ChatNotFoundException
import ru.aigram.exception.impl.ChatUnavailableException
import ru.aigram.exception.impl.MediaUnavailableException
import ru.aigram.storage.AutomatedReplyStore
import ru.aigram.storage.ContextExclusionStore
import ru.aigram.storage.MessageArchiveStore
import ru.aigram.storage.SettingsStore
import ru.aigram.telegram.MediaDownloadPolicy
import ru.aigram.telegram.TelegramGateway
import ru.aigram.telegram.TelegramMediaStore
import java.nio.file.Path
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatHistoryComponentTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `history retains archived deletions automated marks exclusions and media policy`() {
        val original =
            ChatMessage("1", "42", "Original caption", true, 100, "photo", MediaAttachment(7, "image/jpeg", "a.jpg"))
        val gateway = FakeTelegramGateway()
        gateway.messages = listOf(original)
        val archive = MessageArchiveStore(directory, Clock.systemUTC())
        val automated = AutomatedReplyStore(directory)
        val exclusions = ContextExclusionStore(directory)
        val component = component(gateway, archive, automated, exclusions)
        automated.record("42", "1")
        exclusions.update("100", "42", "1", ContextExclusionUpdate())

        val initial = component.history("42", null).single()
        archive.delete("100", "42", listOf(1), permanent = true, fromCache = false)
        gateway.messages = emptyList()
        val deleted = component.deleted("42", null).single()

        assertTrue(initial.automated)
        assertTrue(initial.contextExcluded)
        assertFalse(initial.deleted)
        assertTrue(deleted.deleted)
        assertTrue(deleted.automated)
        assertTrue(deleted.contextExcluded)
        assertEquals("Original caption", deleted.text)
        assertEquals(7, deleted.media?.fileId)
        assertEquals(0, deleted.media?.thumbnailFileId)
    }

    @Test
    fun `gallery uses the same archived context and automated decorations as history`() {
        val message = ChatMessage("9007199254740993", "42", "Photo", true, 100)
        val gateway = object : TelegramGateway by FakeTelegramGateway() {
            override fun gallery(chatId: String, around: String): ChatGallery {
                assertEquals("42", chatId)
                assertEquals(message.id, around)
                return ChatGallery(listOf(message), hasMoreBefore = true, hasMoreAfter = false)
            }
        }
        val archive = MessageArchiveStore(directory, Clock.systemUTC())
        val automated = AutomatedReplyStore(directory)
        automated.record("42", message.id)
        val exclusions = ContextExclusionStore(directory)
        exclusions.update("100", "42", message.id, ContextExclusionUpdate())
        val component = component(gateway, archive, automated, exclusions)

        val result = component.gallery("42", message.id)

        assertTrue(result.hasMoreBefore)
        assertFalse(result.hasMoreAfter)
        assertTrue(result.messages.single().automated)
        assertTrue(result.messages.single().contextExcluded)
        assertEquals(message.text, archive.find("100", "42", message.id)?.text)
        assertFailsWith<ChatNotFoundException> { component.deleted("999", null) }
    }

    @Test
    fun `late history and gallery snapshots preserve confirmed edits from the same second`() {
        val original = ChatMessage("1", "42", "Original", true, 100, editedAt = 101)
        val archive = MessageArchiveStore(directory, Clock.systemUTC())
        archive.record("100", listOf(original))
        val historyEdit = original.copy(text = "Edited during history")
        val galleryEdit = original.copy(text = "Edited during gallery")
        val gateway = object : TelegramGateway by FakeTelegramGateway() {
            override fun history(chatId: String, limit: Int, before: Long): List<ChatMessage> {
                archive.recordEdited("100", listOf(historyEdit))
                return listOf(original)
            }

            override fun gallery(chatId: String, around: String): ChatGallery {
                archive.recordEdited("100", listOf(galleryEdit))
                return ChatGallery(listOf(historyEdit), hasMoreBefore = false, hasMoreAfter = false)
            }
        }
        val component = component(gateway, archive, AutomatedReplyStore(directory), ContextExclusionStore(directory))

        assertEquals(historyEdit, component.history("42", null).single())
        assertEquals(galleryEdit, component.gallery("42", original.id).messages.single())
        archive.delete("100", "42", listOf(1), permanent = true, fromCache = false)
        assertEquals(galleryEdit.text, archive.deleted("100", "42").single().text)
    }

    @Test
    fun `account changes during a history or gallery read do not write into either profile`() {
        val original = ChatMessage("1", "42", "Previous account", true, 100)
        var owner = "100"
        val gateway = object : TelegramGateway by FakeTelegramGateway() {
            override fun status(): TelegramStatus = TelegramStatus(state = "ready", userId = owner)

            override fun history(chatId: String, limit: Int, before: Long): List<ChatMessage> {
                owner = "200"
                return listOf(original)
            }

            override fun gallery(chatId: String, around: String): ChatGallery {
                owner = "200"
                return ChatGallery(listOf(original), hasMoreBefore = false, hasMoreAfter = false)
            }
        }
        val archive = MessageArchiveStore(directory, Clock.systemUTC())
        val component = component(gateway, archive, AutomatedReplyStore(directory), ContextExclusionStore(directory))

        assertFailsWith<ChatUnavailableException> { component.history("42", null) }
        owner = "100"
        assertFailsWith<ChatUnavailableException> { component.gallery("42", original.id) }
        assertNull(archive.find("100", "42", original.id))
        assertNull(archive.find("200", "42", original.id))
    }

    private fun component(
        gateway: TelegramGateway,
        archive: MessageArchiveStore,
        automated: AutomatedReplyStore,
        exclusions: ContextExclusionStore,
    ): ChatHistoryComponent = ChatHistoryComponent(
        gateway,
        automated,
        archive,
        MediaDownloadPolicy(SettingsStore(directory)),
        TelegramMediaStore(directory) { throw MediaUnavailableException() },
        exclusions,
    )
}
