package ru.boltunya.domain

import kotlinx.serialization.Serializable

@Serializable
data class StickerSet(
    val id: String,
    val title: String,
    val name: String,
    val thumbnail: MediaAttachment? = null,
    val count: Int = 0,
)
