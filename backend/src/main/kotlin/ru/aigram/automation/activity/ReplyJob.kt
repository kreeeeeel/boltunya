package ru.aigram.automation.activity

import ru.aigram.automation.MemoryQueueTask
import ru.aigram.automation.proactive.ProactiveTask
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.settings.ReplyProcessingMode
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
    val proactive: ProactiveTask? = null,
    val processingMode: ReplyProcessingMode = ReplyProcessingMode.REPLACE,
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
