package ru.boltunya.domain

import kotlinx.serialization.Serializable

@Serializable
data class ReplyQueueUpdate(val paused: Boolean)
