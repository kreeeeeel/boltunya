package ru.boltunya.domain

import kotlinx.serialization.Serializable

@Serializable
data class ReplyQueueState(val paused: Boolean = false, val queued: Int = 0, val running: Int = 0)
