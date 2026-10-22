package ru.aigram.speech

import java.nio.file.Path

fun interface SpeechComputeProbe {
    fun probe(runtime: Path): SpeechComputeProbeResult
}
