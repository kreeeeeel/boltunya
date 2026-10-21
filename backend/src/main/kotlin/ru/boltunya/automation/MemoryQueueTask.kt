package ru.boltunya.automation

import java.util.concurrent.atomic.AtomicBoolean

class MemoryQueueTask(
    val id: String,
    val chatId: String,
    val accountId: String,
    val title: String,
    val avatarUrl: String,
    val model: String,
    val explicit: Boolean,
    val canStart: () -> Boolean,
    val action: () -> Unit,
    private val userCancelled: () -> Unit,
    private val completed: (Throwable?) -> Unit,
) {
    private val finished = AtomicBoolean()

    fun cancelByUser() {
        userCancelled()
    }

    fun finish(failure: Throwable?) {
        if (finished.compareAndSet(false, true)) completed(failure)
    }
}
