package ru.aigram.speech

import java.nio.file.Path

fun interface SpeechDownloadClient {
    fun download(definition: SpeechAssetDefinition, target: Path, progress: (Long) -> Unit)
}
