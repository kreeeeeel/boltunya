package ru.aigram.speech.reply

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.automation.FakeTelegramGateway
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.speech.OutgoingVoiceNote
import ru.aigram.storage.SettingsStore
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoiceQueueTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `voice reply uses the normal queue and native voice transport without a duplicate text`() {
        val settings = SettingsStore(directory)
        settings.update {
            it.copy(paused = false, replyDelaySeconds = 0, replyIntervalSeconds = 0, voiceInputEnabled = true)
        }
        settings.saveRule("42", ChatRule(enabled = true, delaySeconds = 0))
        val gateway = FakeTelegramGateway()
        val store = VoiceDraftStore(directory, gateway, Clock.systemUTC())
        val output = store.output()
        Files.write(output, byteArrayOf(79, 103, 103, 83))
        val voice = store.register("100", "42", OutgoingVoiceNote(output, 1, byteArrayOf(0)))
        val generated = CountDownLatch(1)
        ReplyCoordinator(settings, gateway, voices = store) { _, _ ->
            generated.countDown()
            ReplyResult("Synthetic spoken answer", 1, 1.0, voice = voice)
        }.use { queue ->
            queue.onMessage(ChatMessage("1", "42", "Voice", false, 0, kind = "voice", topicId = 7))
            assertTrue(generated.await(5, TimeUnit.SECONDS))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            java.util.stream.Stream.generate {
                if (gateway.sentVoices.isEmpty()) {
                    Thread.sleep(10)
                }
                gateway.sentVoices.isEmpty()
            }.takeWhile { it && System.nanoTime() < deadline }.forEach { }
            assertEquals(1, gateway.sentVoices.size)
            assertEquals(listOf(true), gateway.voiceAutomated)
            assertEquals(listOf("1"), gateway.voiceReplyIds)
            assertEquals(listOf(7), gateway.voiceTopicIds)
            assertTrue(gateway.sent.isEmpty())
        }
    }

    @Test
    fun `voice input stays ignored until recognition has explicit consent`() {
        val settings = SettingsStore(directory)
        settings.update { it.copy(paused = false, replyDelaySeconds = 0) }
        settings.saveRule("42", ChatRule(enabled = true, delaySeconds = 0))
        val gateway = FakeTelegramGateway()
        val generated = CountDownLatch(1)
        ReplyCoordinator(settings, gateway) { _, _ ->
            generated.countDown()
            ReplyResult("Must not generate", 0, 0.0)
        }.use { queue ->
            queue.onMessage(ChatMessage("1", "42", "Voice", false, 0, kind = "voice"))
            assertEquals(0, queue.queue().queued)
            assertEquals(1L, generated.count)
            assertTrue(gateway.sentVoices.isEmpty())
            assertTrue(gateway.sent.isEmpty())
        }
    }
}
