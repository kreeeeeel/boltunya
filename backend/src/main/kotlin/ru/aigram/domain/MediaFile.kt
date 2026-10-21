package ru.aigram.domain

import java.nio.file.Path

data class MediaFile(val path: Path, val mimeType: String, val fileName: String)
