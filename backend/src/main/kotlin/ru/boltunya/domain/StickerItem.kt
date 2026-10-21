package ru.boltunya.domain

import kotlinx.serialization.Serializable

@Serializable
data class StickerItem(
    val id: String,
    val emoji: String,
    val setId: String,
    val media: MediaAttachment,
    val width: Int,
    val height: Int,
)
