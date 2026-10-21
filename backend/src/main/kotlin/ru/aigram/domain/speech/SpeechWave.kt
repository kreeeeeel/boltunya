package ru.aigram.domain.speech

import java.nio.file.Path

data class SpeechWave(val path: Path, val sampleRate: Int, val durationMillis: Long)
