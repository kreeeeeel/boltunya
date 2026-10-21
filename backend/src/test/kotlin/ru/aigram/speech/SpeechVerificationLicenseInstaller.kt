package ru.aigram.speech

import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

class SpeechVerificationLicenseInstaller(json: Json, private val failure: AtomicReference<Exception>) :
    SpeechLicenseInstaller(json) {
    override fun copy(target: Path) {
        try {
            super.copy(target)
        } catch (exception: Exception) {
            failure.compareAndSet(null, exception)
            throw exception
        }
    }
}
