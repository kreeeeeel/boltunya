package ru.aigram.speech

import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

private const val MAX_WORKER_REQUEST_BYTES = 65536

fun main(arguments: Array<String>) {
    if (arguments.size != 1) {
        return
    }
    val json = Json
    val result = try {
        val bytes = System.`in`.readNBytes(MAX_WORKER_REQUEST_BYTES + 1)
        if (bytes.size > MAX_WORKER_REQUEST_BYTES) {
            SpeechWorkerResult(error = "INVALID_SPEECH_REQUEST")
        } else {
            val request = json.decodeFromString<SpeechWorkerRequest>(bytes.toString(Charsets.UTF_8))
            SherpaSpeechWorker().execute(request)
        }
    } catch (exception: Exception) {
        SpeechWorkerResult(error = "SPEECH_INFERENCE_FAILED")
    } catch (exception: LinkageError) {
        SpeechWorkerResult(error = "SPEECH_INFERENCE_FAILED")
    }
    Files.writeString(Path.of(arguments[0]), json.encodeToString(result))
}
