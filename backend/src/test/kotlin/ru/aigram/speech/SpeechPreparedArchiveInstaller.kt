package ru.aigram.speech

import java.nio.file.Files
import java.nio.file.Path

class SpeechPreparedArchiveInstaller : SpeechArchiveInstaller() {
    override fun extract(archive: Path, target: Path, definition: SpeechAssetDefinition) {
        Files.createDirectories(target)
        definition.requiredFiles.stream().forEach { name ->
            val path = target.resolve(name)
            if (name in setOf("env", "rvc", "espeak-ng-data")) {
                Files.createDirectories(path)
            } else {
                Files.createDirectories(path.parent)
                Files.write(path, byteArrayOf(1))
            }
        }
    }
}
