package ru.aigram.domain

import kotlinx.serialization.Serializable

@Serializable
data class ReplyQueueUpdate(val paused: Boolean)
