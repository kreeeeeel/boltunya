package ru.aigram.automation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.ReplyResult
import ru.aigram.storage.SettingsStore
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.IntStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReplySettingsCancellationTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `changing reply settings preserves queued and running memory jobs`() {
        val settings = SettingsStore(directory)
        val gateway = FakeTelegramGateway()
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val completed = AtomicBoolean()
        ReplyCoordinator(settings, gateway) { _, _ -> ReplyResult("draft", 0, 0.0) }.use { replies ->
            try {
                replies.enqueueMemory(
                    MemoryQueueTask(
                        "memory", "42", "100", "Chat", "", "gemma3:4b", true, { true },
                        {
                            started.countDown()
                            finish.await(3, TimeUnit.SECONDS)
                        },
                        { error("memory must not be cancelled") },
                        { failure ->
                            assertEquals(null, failure)
                            completed.set(true)
                        },
                    ),
                )
                assertTrue(started.await(3, TimeUnit.SECONDS))
                replies.pauseQueue(true)
                replies.enqueueDraft("42", "gemma3:4b") { ReplyResult("stale", 0, 0.0) }
                replies.cancelReplies()
                assertEquals("generating", replies.activities().single { it.kind.name == "MEMORY" }.status)
                assertEquals("cancelled", replies.activities().single { it.kind.name == "DRAFT" }.status)
                finish.countDown()
                assertTrue(
                    IntStream.range(0, 300).anyMatch {
                        Thread.sleep(10)
                        completed.get()
                    },
                )
                assertEquals("ready", replies.activities().single { it.kind.name == "MEMORY" }.status)
            } finally {
                finish.countDown()
            }
        }
    }
}
