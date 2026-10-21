package ru.aigram.domain.speech

import java.time.Instant

internal data class StoredVoiceDraft(
    val ownerId: String,
    val chatId: String,
    val note: OutgoingVoiceNote,
    val createdAt: Instant,
    var sending: Boolean = false,
    var sentId: String = "",
    val previewOnly: Boolean = false,
)
