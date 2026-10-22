package ru.aigram.automation

import ru.aigram.telegram.TelegramGateway
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class TypingIndicator(private val telegram: TelegramGateway) : AutoCloseable {
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val active = ConcurrentHashMap<String, ScheduledFuture<*>>()

    @Volatile private var closed = false

    @Synchronized
    fun start(chatId: String, allowed: () -> Boolean): ScheduledFuture<*>? {
        if (closed) {
            return null
        }
        val task = executor.scheduleAtFixedRate({
            if (allowed()) {
                send(chatId, true)
            } else {
                cancel(chatId)
            }
        }, 0, 4, TimeUnit.SECONDS)
        active[chatId] = task
        return task
    }

    @Synchronized
    fun cancel(chatId: String) {
        val task = active.remove(chatId)

        if (task == null) {
            return
        }

        task.cancel(false)
        if (!closed) {
            executor.execute { send(chatId, false) }
        }
    }

    @Synchronized
    fun cancelAll() {
        active.keys.stream().forEach(::cancel)
    }

    fun stop(chatId: String, task: ScheduledFuture<*>?) {
        if (task != null && active[chatId] === task) {
            cancel(chatId)
        }
    }

    private fun send(chatId: String, active: Boolean) {
        try {
            telegram.typing(chatId, active)
        } catch (exception: Exception) {
            System.err.println("Typing status failed: ${exception.javaClass.simpleName}")
        }
    }

    @Synchronized
    override fun close() {
        cancelAll()
        closed = true
        executor.shutdown()
    }
}
