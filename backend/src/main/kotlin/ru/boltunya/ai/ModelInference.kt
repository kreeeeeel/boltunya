package ru.boltunya.ai

import jakarta.inject.Inject
import ru.boltunya.exception.impl.BackgroundInferenceDeferredException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

class ModelInference @Inject constructor() {
    private val lock = ReentrantLock(true)
    private val foreground = AtomicInteger()
    private val background = AtomicReference<Thread?>()

    fun <T> foreground(action: () -> T): T {
        foreground.incrementAndGet()
        interruptBackground()
        try {
            lock.lockInterruptibly()
            try {
                return action()
            } finally {
                lock.unlock()
            }
        } finally {
            foreground.decrementAndGet()
        }
    }

    fun <T> background(action: () -> T): T {
        if (foreground.get() > 0 || !lock.tryLock()) throw BackgroundInferenceDeferredException()
        try {
            background.set(Thread.currentThread())
            if (foreground.get() > 0) throw BackgroundInferenceDeferredException()
            return action()
        } catch (_: InterruptedException) {
            throw BackgroundInferenceDeferredException()
        } catch (exception: Exception) {
            if (Thread.currentThread().isInterrupted) throw BackgroundInferenceDeferredException()
            throw exception
        } finally {
            background.set(null)
            Thread.interrupted()
            lock.unlock()
        }
    }

    fun interruptBackground() {
        background.get()?.interrupt()
    }
    fun foregroundBusy(): Boolean = foreground.get() > 0
}
