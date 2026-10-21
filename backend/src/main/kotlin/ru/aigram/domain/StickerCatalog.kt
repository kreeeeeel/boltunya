package ru.aigram.domain

import kotlinx.serialization.Serializable

@Serializable
data class StickerCatalog(val recent: List<StickerItem>, val favorites: List<StickerItem>, val sets: List<StickerSet>)
