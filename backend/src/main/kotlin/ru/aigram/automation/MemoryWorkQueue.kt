package ru.aigram.automation

/** Port used by the memory coordinator; the application binds this to its shared inference scheduler. */
interface MemoryWorkQueue {
    fun enqueueMemory(task: MemoryQueueTask)
    fun cancelMemory(id: String, userInitiated: Boolean = false)
    fun memoryQueuePosition(id: String): Int
}
