package ru.aigram.ai.profile

import org.junit.jupiter.api.Test
import ru.aigram.ai.ModelInference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileInferenceTest {
    @Test
    fun `independent profiles still have a bounded total concurrency`() {
        val inference = ModelInference()
        val started = CountDownLatch(4)
        val release = CountDownLatch(1)
        val fifth = CountDownLatch(1)
        Executors.newFixedThreadPool(5).use { executor ->
            val running = java.util.stream.IntStream.range(0, 4).mapToObj { index ->
                executor.submit {
                    inference.foreground("api:$index") {
                        started.countDown()
                        release.await(3, TimeUnit.SECONDS)
                    }
                }
            }.toList()
            assertTrue(started.await(2, TimeUnit.SECONDS))
            val waiting = executor.submit { inference.foreground("api:fifth") { fifth.countDown() } }
            try {
                assertFalse(fifth.await(100, TimeUnit.MILLISECONDS))
            } finally {
                release.countDown()
            }
            running.stream().forEach { it.get(2, TimeUnit.SECONDS) }
            waiting.get(2, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `independent remote inference does not block background work on a local resource`() {
        val inference = ModelInference()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        Executors.newSingleThreadExecutor().use { executor ->
            val remote = executor.submit {
                inference.foreground("api:remote") {
                    started.countDown()
                    release.await(3, TimeUnit.SECONDS)
                }
            }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            try {
                assertEquals("memory", inference.background("local") { "memory" })
                assertFalse(inference.foregroundBusy("local"))
            } finally {
                release.countDown()
            }
            remote.get(2, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `the same local resource is serialized even when independent scheduling is enabled`() {
        val inference = ModelInference()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val first = executor.submit {
                inference.foreground("local") {
                    started.countDown()
                    release.await(3, TimeUnit.SECONDS)
                }
            }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            val second = executor.submit { inference.foreground("local") { secondEntered.countDown() } }
            try {
                assertFalse(secondEntered.await(100, TimeUnit.MILLISECONDS))
            } finally {
                release.countDown()
            }
            first.get(2, TimeUnit.SECONDS)
            second.get(2, TimeUnit.SECONDS)
            assertEquals(0, secondEntered.count)
        }
    }

    @Test
    fun `switching from shared to independent mode cannot overlap a currently shared inference`() {
        val inference = ModelInference()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val first = executor.submit {
                inference.foreground {
                    started.countDown()
                    release.await(3, TimeUnit.SECONDS)
                }
            }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            val second = executor.submit { inference.foreground("local") { secondEntered.countDown() } }
            try {
                assertFalse(secondEntered.await(100, TimeUnit.MILLISECONDS))
            } finally {
                release.countDown()
            }
            first.get(2, TimeUnit.SECONDS)
            second.get(2, TimeUnit.SECONDS)
        }
    }
}
