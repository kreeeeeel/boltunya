package ru.aigram.automation.activity

import ru.aigram.automation.MemoryQueueTask
import ru.aigram.domain.ChatMessage
import ru.aigram.domain.ReplyResult
import java.util.concurrent.atomic.AtomicBoolean

class ReplyJob(
    val id: String,
    val message: ChatMessage,
    val trace: ReplyTrace,
    var readyAt: Long,
    val waitUntil: Long,
    var order: Long,
    val draft: (() -> ReplyResult)? = null,
    val accountId: String = "",
    val memory: MemoryQueueTask? = null,
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
