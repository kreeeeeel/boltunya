package ru.aigram.domain.media

import java.nio.file.Path

data class OutgoingMedia(val path: Path, val kind: OutgoingMediaKind, val caption: String = "")
