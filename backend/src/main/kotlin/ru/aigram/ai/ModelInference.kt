package ru.aigram.ai

import jakarta.inject.Inject
import org.springframework.stereotype.Component
import ru.aigram.automation.activity.ActivityPhase
import ru.aigram.automation.activity.ReplyTraceScope
import ru.aigram.exception.impl.BackgroundInferenceDeferredException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.locks.ReentrantReadWriteLock

private const val MAX_INDEPENDENT_INFERENCES = 4

@Component
class ModelInference @Inject constructor(private val tracing: ReplyTraceScope) {
    constructor() : this(ReplyTraceScope())

    private val resources = ConcurrentHashMap<String, InferenceResource>()
    private val scheduling = ReentrantReadWriteLock(true)
    private val independent = Semaphore(MAX_INDEPENDENT_INFERENCES, true)

    fun <T> foreground(action: () -> T): T = foreground("shared", action)

    fun <T> foreground(group: String, action: () -> T): T {
        val previousPhase = tracing.current()?.currentPhase()
        tracing.phase(ActivityPhase.WAITING_RESOURCE)
        val resource = resources.computeIfAbsent(group) { InferenceResource() }
        val mode = if (group == "shared") scheduling.writeLock() else scheduling.readLock()
        resource.foreground.incrementAndGet()
        interruptBackground(group)
        try {
            mode.lockInterruptibly()
            try {
                resource.lock.lockInterruptibly()
                var acquired = false
                try {
                    if (group != "shared") {
                        independent.acquire()
                        acquired = true
                    }
                    if (previousPhase != null) tracing.phase(previousPhase)
                    return action()
                } finally {
                    if (acquired) independent.release()
                    resource.lock.unlock()
                }
            } finally {
                mode.unlock()
            }
        } finally {
            resource.foreground.decrementAndGet()
        }
    }

    fun <T> background(action: () -> T): T = background("shared", action)

    fun <T> background(group: String, action: () -> T): T {
        val resource = resources.computeIfAbsent(group) { InferenceResource() }
        val mode = if (group == "shared") scheduling.writeLock() else scheduling.readLock()
        if (foregroundBusy(group) || !mode.tryLock()) throw BackgroundInferenceDeferredException()
        if (!resource.lock.tryLock()) {
            mode.unlock()
            throw BackgroundInferenceDeferredException()
        }
        if (group != "shared" && !independent.tryAcquire()) {
            resource.lock.unlock()
            mode.unlock()
            throw BackgroundInferenceDeferredException()
        }
        try {
            resource.background.set(Thread.currentThread())
            if (foregroundBusy(group)) throw BackgroundInferenceDeferredException()
            return action()
        } catch (_: InterruptedException) {
            throw BackgroundInferenceDeferredException()
        } catch (exception: Exception) {
            if (Thread.currentThread().isInterrupted) throw BackgroundInferenceDeferredException()
            throw exception
        } finally {
            resource.background.set(null)
            Thread.interrupted()
            resource.lock.unlock()
            mode.unlock()
            if (group != "shared") independent.release()
        }
    }

    fun interruptBackground() {
        resources.values.stream().forEach { it.background.get()?.interrupt() }
    }

    fun interruptBackground(group: String) {
        if (group == "shared") {
            interruptBackground()
        } else {
            resources[group]?.background?.get()?.interrupt()
            resources["shared"]?.background?.get()?.interrupt()
        }
    }

    fun foregroundBusy(): Boolean = resources.values.stream().anyMatch { it.foreground.get() > 0 }

    fun foregroundBusy(group: String): Boolean = if (group == "shared") {
        foregroundBusy()
    } else {
        (resources[group]?.foreground?.get() ?: 0) > 0 || (resources["shared"]?.foreground?.get() ?: 0) > 0
    }
}
