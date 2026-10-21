package ru.aigram.domain.events

import kotlinx.serialization.Serializable

@Serializable
data class ReplyQueueUpdate(val paused: Boolean)
