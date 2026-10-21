package ru.aigram.speech

import ru.aigram.domain.speech.SpeechImportedFiles
import ru.aigram.speech.reply.VoiceTestGateway
import java.nio.file.Path

class OwnerSwitchingSpeechImporter(private val gateway: VoiceTestGateway) : SpeechModelArchiveImporter() {
    override fun extract(archive: Path, staging: Path): SpeechImportedFiles {
        val files = super.extract(archive, staging)
        gateway.owner = "200"
        return files
    }
}
