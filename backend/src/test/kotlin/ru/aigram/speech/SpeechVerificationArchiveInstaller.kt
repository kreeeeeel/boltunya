package ru.aigram.speech

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

class SpeechVerificationArchiveInstaller(private val failure: AtomicReference<Exception>) : SpeechArchiveInstaller() {
    override fun extract(archive: Path, target: Path, definition: SpeechAssetDefinition) {
        try {
            super.extract(archive, target, definition)
        } catch (exception: Exception) {
            failure.compareAndSet(null, exception)
            throw exception
        }
    }
}
