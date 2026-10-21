package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.telegram.TelegramStatus
import ru.aigram.storage.AutomatedReplyStore
import ru.aigram.storage.CredentialStore
import java.nio.file.Path
import java.time.Clock
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelegramVoiceProvenanceTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `voice updates retain exact automated provenance while identical manual voice updates stay manual`() {
        val cache = TelegramCache().apply { ownId = 100 }
        val connection = TelegramConnectionState(CredentialStore(directory)).apply {
            version = 7
            status = TelegramStatus(state = "ready", userId = "100")
        }
        val store = AutomatedReplyStore(directory)
        val registry = TelegramAutomatedSendRegistry(connection, cache, store, Clock.systemUTC())
        val token = registry.begin(42)
        val content = TdApi.MessageVoiceNote().apply { voiceNote = TdApi.VoiceNote().apply { duration = 2 } }
        val pending = TdApi.Message().apply {
            id = -1
            chatId = 42
            isOutgoing = true
            sendingState = TdApi.MessageSendingStatePending(token.sendingId)
            this.content = content
        }
        val manual = TdApi.Message().apply {
            id = -2
            chatId = 42
            isOutgoing = true
            sendingState = TdApi.MessageSendingStatePending(0)
            this.content = content
        }
        assertTrue(registry.pending(pending))
        assertFalse(registry.pending(manual))
        val delivered = TdApi.Message().apply {
            id = 9007199254740993L
            chatId = 42
            isOutgoing = true
            this.content = content
        }
        assertTrue(registry.succeeded(pending.id, delivered))
        assertTrue(store.contains("42", delivered.id.toString()))
        assertTrue(registry.known(delivered))
        connection.version++
        assertFalse(registry.known(delivered))
    }
}
