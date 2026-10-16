package ru.boltunya.automation.activity

import ru.boltunya.domain.ChatMessage
import java.util.concurrent.atomic.AtomicBoolean

class ReplyJob(
    val id: String,
    val message: ChatMessage,
    val trace: ReplyTrace,
    val readyAt: Long,
    val waitUntil: Long,
    var order: Long,
) {
    val cancelled = AtomicBoolean()
    var priority = false

    @Volatile var delivering = false

    @Volatile var thread: Thread? = null

    fun cancel() {
        cancelled.set(true)
        thread?.interrupt()
    }
}
