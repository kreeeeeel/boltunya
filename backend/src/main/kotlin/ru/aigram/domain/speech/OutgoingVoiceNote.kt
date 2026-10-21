package ru.aigram.domain.speech

import java.nio.file.Path

data class OutgoingVoiceNote(val path: Path, val duration: Int, val waveform: ByteArray, val ownerId: String = "")
