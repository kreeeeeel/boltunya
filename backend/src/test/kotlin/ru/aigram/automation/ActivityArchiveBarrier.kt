package ru.aigram.automation

import ru.aigram.automation.activity.ActivityHistoryStore
import java.util.Arrays
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.IntStream
import kotlin.test.assertTrue

class ActivityArchiveBarrier(private val history: ActivityHistoryStore) : AutoCloseable {
    private val executor = Executors.newFixedThreadPool(2)
    private val held = CountDownLatch(1)
    private val released = CountDownLatch(1)
    private val reader = AtomicReference<Thread>()
    private var holder: Future<*>? = null

    fun hold() {
        holder = executor.submit {
            synchronized(history) {
                held.countDown()
                assertTrue(released.await(10, TimeUnit.SECONDS), "Synthetic archive hold expired")
            }
        }
        assertTrue(held.await(3, TimeUnit.SECONDS), "Archive monitor was not acquired")
    }

    fun <T> read(action: () -> T): Future<T> = executor.submit(
        Callable<T> {
            reader.set(Thread.currentThread())
            action()
        },
    )

    fun awaitReader() {
        assertTrue(
            IntStream.range(0, 3000).anyMatch {
                val thread = reader.get()
                if (thread != null && thread.state == Thread.State.BLOCKED &&
                    Arrays.stream(thread.stackTrace).anyMatch { frame ->
                        frame.className == ActivityHistoryStore::class.java.name && frame.methodName == "details"
                    }
                ) {
                    true
                } else {
                    TimeUnit.MILLISECONDS.sleep(1)
                    false
                }
            },
            "Details did not reach the owned archive monitor",
        )
    }

    fun release() = released.countDown()

    override fun close() {
        release()
        executor.shutdown()
        if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS), "Archive fixture threads did not stop")
        }
        holder?.get(3, TimeUnit.SECONDS)
    }
}
