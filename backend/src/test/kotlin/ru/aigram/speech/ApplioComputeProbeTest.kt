package ru.aigram.speech

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.exception.impl.SpeechInferenceFailedException
import ru.aigram.exception.impl.SpeechTimeoutException
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ApplioComputeProbeTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `overlapping requests share only the active metadata read and the next request is fresh`() {
        val probe = probe()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val reader = AtomicReference<Thread>()
        val result = SpeechComputeProbeResult(true, "Synthetic GPU", 8585216000, "2.11.0+cu128", "12.8")
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit<SpeechComputeProbeResult> {
                probe.coalesce {
                    calls.incrementAndGet()
                    started.countDown()
                    assertTrue(release.await(5, TimeUnit.SECONDS))
                    result
                }
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            val second = executor.submit<SpeechComputeProbeResult> {
                reader.set(Thread.currentThread())
                probe.coalesce { throw AssertionError("Concurrent request must not start another torch process") }
            }
            awaitFollower(reader)
            release.countDown()
            assertSame(result, first.get(5, TimeUnit.SECONDS))
            assertSame(result, second.get(5, TimeUnit.SECONDS))
            assertEquals(1, calls.get())
            val fresh = probe.coalesce {
                calls.incrementAndGet()
                result.copy(cudaAvailable = false, gpuName = "", gpuMemoryBytes = 0)
            }
            assertEquals(false, fresh.cudaAvailable)
            assertEquals(2, calls.get())
        } finally {
            release.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `interrupting a follower does not cancel the owned probe or poison the following refresh`() {
        val probe = probe()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val reader = AtomicReference<Thread>()
        val result = SpeechComputeProbeResult(false, "", 0, "2.11.0+cpu", "")
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit<SpeechComputeProbeResult> {
                probe.coalesce {
                    started.countDown()
                    assertTrue(release.await(5, TimeUnit.SECONDS))
                    result
                }
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            val follower = executor.submit<SpeechComputeProbeResult> {
                reader.set(Thread.currentThread())
                probe.coalesce { throw AssertionError("Follower must share the existing request") }
            }
            awaitFollower(reader)
            reader.get().interrupt()
            assertIs<InterruptedException>(
                assertFailsWith<ExecutionException> { follower.get(5, TimeUnit.SECONDS) }.cause,
            )
            assertEquals(false, first.isDone)
            release.countDown()
            assertEquals(result, first.get(5, TimeUnit.SECONDS))
            assertEquals(result, probe.coalesce { result })
        } finally {
            release.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `failed owner releases all followers and permits a later explicit retry`() {
        val probe = probe()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val reader = AtomicReference<Thread>()
        val executor = Executors.newFixedThreadPool(2)
        try {
            val owner = executor.submit<SpeechComputeProbeResult> {
                probe.coalesce {
                    started.countDown()
                    assertTrue(release.await(5, TimeUnit.SECONDS))
                    throw SpeechTimeoutException()
                }
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            val follower = executor.submit<SpeechComputeProbeResult> {
                reader.set(Thread.currentThread())
                probe.coalesce { throw AssertionError("Failure must be shared without duplicate process") }
            }
            awaitFollower(reader)
            release.countDown()
            assertIs<SpeechTimeoutException>(
                assertFailsWith<ExecutionException> { owner.get(5, TimeUnit.SECONDS) }.cause,
            )
            assertIs<SpeechInferenceFailedException>(
                assertFailsWith<ExecutionException> { follower.get(5, TimeUnit.SECONDS) }.cause,
            )
            assertEquals(false, probe.coalesce { SpeechComputeProbeResult(false, "", 0, "cpu", "") }.cudaAvailable)
        } finally {
            release.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    private fun awaitFollower(reader: AtomicReference<Thread>) {
        awaitSpeech {
            reader.get()?.stackTrace?.asList()?.stream()?.anyMatch {
                it.className == "java.util.concurrent.CompletableFuture"
            } == true
        }
    }

    private fun probe(): ApplioComputeProbe = ApplioComputeProbe(
        directory,
        Json,
        ApplioSpeechWorker(),
        SpeechWorkerProcess(Json, ApplioSpeechWorker()),
    )
}
