package ru.boltunya.media

import ru.boltunya.domain.UploadedFile
import java.nio.file.Path

internal data class StoredUpload(
    val ownerId: String,
    val file: UploadedFile,
    val path: Path,
    val createdAt: Long,
    val inUse: Boolean = false,
    val discardRequested: Boolean = false,
)
