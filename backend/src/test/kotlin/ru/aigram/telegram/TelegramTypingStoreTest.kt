package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.junit.jupiter.api.Test
import ru.aigram.domain.TypingAction
import ru.aigram.memory.AdvancingClock
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TelegramTypingStoreTest {
    @Test
    fun `tracks remote typing and clears cancel received messages and expired actions`() {
        val clock = AdvancingClock(Instant.parse("2026-10-21T12:00:00Z"))
        val cache = TelegramCache().apply { ownId = 1 }
        cache.users[2] = TdApi.User().apply {
            firstName = "Anna"
            lastName = "Example"
        }
        val store = TelegramTypingStore(cache, clock)
        val sender = TdApi.MessageSenderUser(2)
        store.update(100, sender, TdApi.ChatActionTyping())
        assertEquals("Anna Example", store.list(100).single().name)
        assertEquals(TypingAction.TYPING, store.list(100).single().action)
        store.update(100, sender, TdApi.ChatActionUploadingPhoto())
        assertEquals(TypingAction.UPLOAD_PHOTO, store.list(100).single().action)
        store.update(100, sender, TdApi.ChatActionCancel())
        assertTrue(store.list(100).isEmpty())
        store.update(100, sender, TdApi.ChatActionTyping())
        store.clear(100, sender)
        assertTrue(store.list(100).isEmpty())
        store.update(100, sender, TdApi.ChatActionTyping())
        clock.advance(Duration.ofSeconds(7))
        assertTrue(store.list(100).isEmpty())
    }

    @Test
    fun `keeps group participants separate excludes owner and resets on account switch`() {
        val clock = AdvancingClock(Instant.parse("2026-10-21T12:00:00Z"))
        val cache = TelegramCache().apply { ownId = 1 }
        val store = TelegramTypingStore(cache, clock)
        store.update(100, TdApi.MessageSenderUser(1), TdApi.ChatActionTyping())
        assertTrue(store.list(100).isEmpty())
        store.update(100, TdApi.MessageSenderUser(2), TdApi.ChatActionTyping())
        store.update(100, TdApi.MessageSenderUser(3), TdApi.ChatActionRecordingVoiceNote())
        assertEquals(listOf("user:2", "user:3"), store.list(100).map { it.id })
        assertTrue(store.list(101).isEmpty())
        cache.ownId = 4
        assertTrue(store.list(100).isEmpty())
    }
}
