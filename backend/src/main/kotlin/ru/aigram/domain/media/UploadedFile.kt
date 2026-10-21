package ru.aigram.domain.media

import kotlinx.serialization.Serializable

@Serializable
data class UploadedFile(val id: String, val fileName: String, val mimeType: String, val size: Long)
