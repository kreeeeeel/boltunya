package ru.aigram.speech.reply

import ru.aigram.domain.speech.OutgoingVoiceNote
import ru.aigram.speech.codec.AudioCodec
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class SpeechHistoryTestCodec(directory: Path) : AudioCodec(directory) {
    override fun toRecognitionWave(input: Path, output: Path): Path =
        Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING)

    override fun toVoiceNote(wav: Path, output: Path): OutgoingVoiceNote {
        Files.writeString(output, "OggSsynthetic")
        return OutgoingVoiceNote(output, 2, ByteArray(63) { 1 })
    }
}
