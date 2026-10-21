package ru.aigram.domain

import kotlinx.serialization.Serializable

@Serializable
data class MessageDeletionRequest(val scope: MessageDeletionScope)
