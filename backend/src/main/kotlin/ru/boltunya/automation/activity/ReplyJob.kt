package ru.boltunya.automation.activity

import ru.boltunya.automation.MemoryQueueTask
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ReplyResult
import java.util.concurrent.atomic.AtomicBoolean

class ReplyJob(
    val id: String,
    val message: ChatMessage,
    val trace: ReplyTrace,
    val readyAt: Long,
    val waitUntil: Long,
    var order: Long,
    val draft: (() -> ReplyResult)? = null,
    val accountId: String = "",
    val memory: MemoryQueueTask? = null,
) {
    val cancelled = AtomicBoolean()
    var priority = false
    var preempted = false

    @Volatile var delivering = false

    @Volatile var thread: Thread? = null

    fun cancel() {
        cancelled.set(true)
        thread?.interrupt()
    }
}
