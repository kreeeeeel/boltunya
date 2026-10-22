package ru.aigram.speech.reply

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.automation.FakeTelegramGateway
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.chat.VoiceSettingsMode
import ru.aigram.domain.settings.VoiceReplyMode
import ru.aigram.speech.awaitSpeech
import ru.aigram.storage.SettingsStore
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatVoiceQueueTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `global speech changes cancel inherited work but preserve chat overrides and explicit manual drafts`() {
        val settings = SettingsStore(directory)
        settings.update { it.copy(paused = false, replyDelaySeconds = 0, replyIntervalSeconds = 0) }
        settings.saveRule(
            "42",
            ChatRule(
                enabled = true,
                voiceSettingsMode = VoiceSettingsMode.OVERRIDE,
                voiceReplyMode = VoiceReplyMode.MIRROR,
                voiceInputEnabled = true,
            ),
        )
        settings.saveRule("43", ChatRule(enabled = true))
        val gateway = FakeTelegramGateway()
        ReplyCoordinator(settings, gateway) { _, history ->
            ReplyResult("Chat ${history.last().chatId}", 0, 0.0)
        }.use { queue ->
            queue.pauseQueue(true)
            queue.onMessage(ChatMessage("1", "42", "Synthetic voice", false, 0, kind = "voice"))
            queue.onMessage(ChatMessage("2", "43", "Synthetic text", false, 0))
            val manual = queue.enqueueDraft("43", "piper-en-amy") { ReplyResult("Explicit manual", 0, 0.0) }
            assertEquals(3, queue.queue().queued)
            val before = settings.get()
            val after = settings.update { it.copy(voiceReplyMode = VoiceReplyMode.ALWAYS, voiceSpeechRate = 1.5) }

            queue.cancelVoiceReplies(before, after)

            assertEquals(2, queue.queue().queued)
            assertEquals("cancelled", queue.activities().single { it.chatId == "43" && it.id != manual.id }.status)
            assertEquals("queued", queue.draft(manual.id).status)
            queue.pauseQueue(false)
            awaitSpeech { gateway.sent.size == 1 && queue.draft(manual.id).status == "ready" }
            assertEquals(listOf("Chat 42"), gateway.sent)
            assertEquals("Explicit manual", queue.draft(manual.id).result?.text)
        }
    }

    @Test
    fun `effective chat settings changing during generation prevent stale delivery`() {
        val settings = SettingsStore(directory)
        settings.update { it.copy(paused = false, replyDelaySeconds = 0, replyIntervalSeconds = 0) }
        settings.saveRule("42", ChatRule(enabled = true))
        val gateway = FakeTelegramGateway()
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        ReplyCoordinator(settings, gateway) { _, _ ->
            entered.countDown()
            assertTrue(released.await(5, TimeUnit.SECONDS))
            ReplyResult("Stale voice preferences", 0, 0.0)
        }.use { queue ->
            queue.onMessage(ChatMessage("1", "42", "Synthetic text", false, 0))
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            settings.update { it.copy(voiceReplyMode = VoiceReplyMode.ALWAYS) }
            released.countDown()

            awaitSpeech { queue.activities().single().status == "cancelled" }
            assertTrue(gateway.sent.isEmpty())
            assertTrue(gateway.sentVoices.isEmpty())
        }
    }
}
