package ru.boltunya.domain

import kotlinx.serialization.Serializable

@Serializable
data class UploadedFile(val id: String, val fileName: String, val mimeType: String, val size: Long)
