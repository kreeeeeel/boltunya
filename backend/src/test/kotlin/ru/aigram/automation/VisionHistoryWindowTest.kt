package ru.aigram.automation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatRule
import ru.aigram.storage.SettingsStore
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.stream.IntStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VisionHistoryWindowTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `small text history preference does not hide part of the latest photo album`() {
        val settings = SettingsStore(directory)
        settings.update { it.copy(paused = false, replyDelaySeconds = 0, replyIntervalSeconds = 0) }
        settings.saveRule(
            "42",
            ChatRule(enabled = true, matchMyStyle = false, contextMessages = 4, analyzePhotos = true),
        )
        val gateway = FakeTelegramGateway()
        gateway.messages = IntStream.rangeClosed(1, 10).mapToObj {
            ChatMessage(it.toString(), "42", "Photo $it", false, 1, kind = "photo", mediaAlbumId = "album")
        }.toList()
        val ready = CountDownLatch(1)
        ReplyCoordinator(settings, gateway) { _, history ->
            assertEquals(10, history.size)
            assertEquals("1", history.first().id)
            ready.countDown()
            ReplyResult("Synthetic reply", 0, 0.0)
        }.use { queue ->
            queue.onMessage(gateway.messages.last())
            assertTrue(ready.await(5, TimeUnit.SECONDS))
        }
    }
}
