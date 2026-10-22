package ru.aigram.speech

import com.k2fsa.sherpa.onnx.OfflineRecognizer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import ru.aigram.exception.impl.SpeechInferenceFailedException
import ru.aigram.exception.impl.SpeechTimeoutException
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

private const val MAX_WORKER_RESULT_BYTES = 131072L
private const val WORKER_HEAP = "-Xmx512m"
private const val WORKER_MAIN = "ru.aigram.speech.SpeechWorkerMainKt"
private const val WORKER_STOP_SECONDS = 5L
private val WORKER_TIMEOUT = Duration.ofMinutes(10)

@Component
class SpeechWorkerProcess(private val json: Json, private val applio: ApplioSpeechWorker) : SpeechProcessExecutor {
    override fun execute(nativeJar: Path, request: SpeechWorkerRequest, result: Path): SpeechWorkerResult {
        val conversion = request.operation == "conversion"
        val builder = if (conversion) {
            applio.builder(request, result.toAbsolutePath().parent)
        } else {
            builder(nativeJar, result)
        }
        val process = builder.start()
        try {
            process.outputStream.use {
                if (!conversion) {
                    it.write(json.encodeToString(request).toByteArray(Charsets.UTF_8))
                }
            }
            if (!process.waitFor(WORKER_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw SpeechTimeoutException()
            }
            if (process.exitValue() != 0) {
                throw SpeechInferenceFailedException()
            }
            if (conversion) {
                return applio.result(Path.of(request.output))
            }
            if (!Files.isRegularFile(result) ||
                Files.size(result) > MAX_WORKER_RESULT_BYTES
            ) {
                throw SpeechInferenceFailedException()
            }
            val response = json.decodeFromString<SpeechWorkerResult>(Files.readString(result))
            if (response.error.isNotEmpty()) {
                throw SpeechInferenceFailedException()
            }
            return response
        } finally {
            stop(process)
        }
    }

    internal fun stop(process: Process) {
        if (!process.isAlive) {
            return
        }
        var interrupted = Thread.interrupted()
        try {
            process.descendants().forEach { it.destroyForcibly() }
            process.destroyForcibly()
            try {
                process.waitFor(WORKER_STOP_SECONDS, TimeUnit.SECONDS)
            } catch (exception: InterruptedException) {
                interrupted = true
                process.destroyForcibly()
                process.waitFor(WORKER_STOP_SECONDS, TimeUnit.SECONDS)
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt()
            }
        }
    }

    internal fun builder(nativeJar: Path, result: Path): ProcessBuilder {
        val java = Path.of(System.getProperty("java.home"), "bin", "java.exe")
        val resultPath = result.toAbsolutePath().normalize()
        val locations = listOf(
            SherpaSpeechWorker::class.java,
            OfflineRecognizer::class.java,
            Json::class.java,
            KSerializer::class.java,
            Unit::class.java,
        ).stream().map { Path.of(it.protectionDomain.codeSource.location.toURI()).toString() }.toList()
        val additional = listOf(System.getProperty("java.class.path"), nativeJar.toAbsolutePath().toString())
        val classpath = (locations + additional)
            .joinToString(File.pathSeparator)
        return ProcessBuilder(
            java.toString(),
            WORKER_HEAP,
            "-Dfile.encoding=UTF-8",
            "-Djava.io.tmpdir=${resultPath.parent}",
            "-cp",
            classpath,
            WORKER_MAIN,
            resultPath.toString(),
        ).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD)
    }
}
