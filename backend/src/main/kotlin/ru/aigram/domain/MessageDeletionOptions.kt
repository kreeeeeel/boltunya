package ru.aigram.domain

import kotlinx.serialization.Serializable

@Serializable
data class MessageDeletionOptions(val canDeleteForSelf: Boolean, val canDeleteForEveryone: Boolean)
