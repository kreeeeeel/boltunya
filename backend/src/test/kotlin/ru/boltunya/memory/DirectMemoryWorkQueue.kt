package ru.boltunya.memory

import ru.boltunya.automation.MemoryQueueTask
import ru.boltunya.automation.MemoryWorkQueue
import ru.boltunya.exception.impl.ReplyCancelledException
import java.util.concurrent.ConcurrentHashMap

class DirectMemoryWorkQueue : MemoryWorkQueue {
    private val requests = ConcurrentHashMap<String, MemoryQueueTask>()
    private val running = ConcurrentHashMap<String, Thread>()

    override fun enqueueMemory(task: MemoryQueueTask) {
        requests[task.id] = task
        if (!task.canStart()) return
        running[task.id] = Thread.currentThread()
        var failure: Throwable? = null
        try {
            task.action()
        } catch (exception: Exception) {
            failure = if (exception is InterruptedException) ReplyCancelledException() else exception
        } finally {
            running.remove(task.id)
            requests.remove(task.id)
            task.finish(failure)
        }
    }

    override fun memoryQueuePosition(id: String): Int = if (id in requests && id !in running) 1 else 0

    override fun cancelMemory(id: String, userInitiated: Boolean) {
        val thread = running[id]
        if (thread != null) {
            thread.interrupt()
        } else {
            val task = requests.remove(id) ?: return
            if (userInitiated) task.cancelByUser()
            task.finish(ReplyCancelledException())
        }
    }
}
