package ru.boltunya.domain

import kotlinx.serialization.Serializable

@Serializable
data class MediaAttachment(
    val fileId: Int,
    val mimeType: String,
    val fileName: String,
    val width: Int = 0,
    val height: Int = 0,
    val thumbnailFileId: Int = 0,
    val size: Long = 0,
    val duration: Int = 0,
    val waveform: List<Int> = emptyList(),
    val autoDownload: Boolean = true,
    val downloaded: Boolean = false,
    val cacheVersion: Long = 0,
    val uniqueId: String = "",
)
