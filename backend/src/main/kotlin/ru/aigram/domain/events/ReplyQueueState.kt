package ru.aigram.domain.events

import kotlinx.serialization.Serializable

@Serializable
data class ReplyQueueState(
    val paused: Boolean = false,
    val queued: Int = 0,
    val running: Int = 0,
    val maxParallelTasks: Int = 1,
    val activeTasks: Int = running,
)
