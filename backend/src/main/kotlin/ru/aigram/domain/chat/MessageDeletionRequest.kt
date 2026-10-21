package ru.aigram.domain.chat

import kotlinx.serialization.Serializable

@Serializable
data class MessageDeletionRequest(val scope: MessageDeletionScope)
