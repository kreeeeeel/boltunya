package ru.aigram.domain.media

import java.nio.file.Path

data class MediaFile(val path: Path, val mimeType: String, val fileName: String)
