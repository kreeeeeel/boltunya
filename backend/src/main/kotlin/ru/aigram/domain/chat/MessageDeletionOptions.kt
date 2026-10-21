package ru.aigram.domain.chat

import kotlinx.serialization.Serializable

@Serializable
data class MessageDeletionOptions(val canDeleteForSelf: Boolean, val canDeleteForEveryone: Boolean)
