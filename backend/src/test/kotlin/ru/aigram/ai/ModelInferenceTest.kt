package ru.aigram.ai

import org.junit.jupiter.api.Test
import ru.aigram.exception.impl.BackgroundInferenceDeferredException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ModelInferenceTest {
    @Test
    fun `foreground reply interrupts a running background summary and can acquire the model`() {
        val inference = ModelInference()
        val started = CountDownLatch(1)
        Executors.newSingleThreadExecutor().use { executor ->
            val background = executor.submit<Boolean> {
                assertFailsWith<BackgroundInferenceDeferredException> {
                    inference.background {
                        started.countDown()
                        CountDownLatch(1).await(10, TimeUnit.SECONDS)
                    }
                }
                true
            }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            assertEquals("reply", inference.foreground { "reply" })
            assertTrue(background.get(2, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `does not start a background generation while a foreground request owns the model`() {
        val inference = ModelInference()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        Executors.newSingleThreadExecutor().use { executor ->
            val foreground = executor.submit {
                inference.foreground {
                    started.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }
            }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            try {
                assertFailsWith<BackgroundInferenceDeferredException> { inference.background { "must not run" } }
            } finally {
                release.countDown()
            }
            foreground.get(2, TimeUnit.SECONDS)
        }
    }
}
