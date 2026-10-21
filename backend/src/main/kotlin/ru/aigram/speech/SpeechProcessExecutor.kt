package ru.aigram.speech

import java.nio.file.Path

fun interface SpeechProcessExecutor {
    fun execute(nativeJar: Path, request: SpeechWorkerRequest, result: Path): SpeechWorkerResult
}
