package ru.aigram.automation

import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.stream.IntStream
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TypingIndicatorTest {
    @Test
    fun `concurrent worker stop cancellation and queue close safely cancel the last typing task`() {
        Executors.newFixedThreadPool(3).use { workers ->
            IntStream.range(0, 200).forEach {
                TypingIndicator(FakeTelegramGateway()).use { indicator ->
                    val task = assertNotNull(indicator.start("42") { true })
                    val released = CountDownLatch(1)
                    val actions = listOf<() -> Unit>(
                        { indicator.stop("42", task) },
                        indicator::cancelAll,
                        indicator::close,
                    )
                    val futures = actions.stream().map { action ->
                        workers.submit<Unit> {
                            assertTrue(released.await(5, TimeUnit.SECONDS))
                            action()
                        }
                    }.toList()

                    released.countDown()
                    futures.stream().forEach { future -> future.get(5, TimeUnit.SECONDS) }
                    assertTrue(task.isCancelled)
                    assertNull(indicator.start("43") { true })
                    indicator.cancelAll()
                    indicator.close()
                }
            }
        }
    }
}
