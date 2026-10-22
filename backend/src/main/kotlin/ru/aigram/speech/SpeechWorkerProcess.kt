package ru.aigram.speech

import com.k2fsa.sherpa.onnx.OfflineRecognizer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import ru.aigram.domain.speech.SpeechComputeDevice
import ru.aigram.exception.impl.SpeechGpuUnavailableException
import ru.aigram.exception.impl.SpeechInferenceFailedException
import ru.aigram.exception.impl.SpeechTimeoutException
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val MAX_WORKER_RESULT_BYTES = 131072L
private const val WORKER_HEAP = "-Xmx512m"
private const val WORKER_MAIN = "ru.aigram.speech.SpeechWorkerMainKt"
private const val WORKER_STOP_SECONDS = 5L
private val WORKER_TIMEOUT = Duration.ofMinutes(10)

@Component
class SpeechWorkerProcess(private val json: Json, private val applio: ApplioSpeechWorker) :
    SpeechProcessExecutor,
    AutoCloseable {
    private val active = ConcurrentHashMap.newKeySet<Process>()
    private var closed = false

    override fun execute(nativeJar: Path, request: SpeechWorkerRequest, result: Path): SpeechWorkerResult {
        val conversion = request.operation == "conversion"
        val builder = if (conversion) {
            applio.builder(request, result.toAbsolutePath().parent)
        } else {
            builder(nativeJar, result)
        }
        return run(builder) { process ->
            writeRequest(process, request)
            if (!process.waitFor(WORKER_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw SpeechTimeoutException()
            }
            if (process.exitValue() != 0) {
                if (conversion && request.conversionDevice == SpeechComputeDevice.CUDA &&
                    process.exitValue() == GPU_UNAVAILABLE_EXIT
                ) {
                    throw SpeechGpuUnavailableException()
                }
                throw SpeechInferenceFailedException()
            }
            if (conversion) {
                val device = applio.verifiedDevice(request.conversionDevice, result.toAbsolutePath().parent)
                return@run applio.result(Path.of(request.output)).copy(computeDevice = device)
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
            response
        }
    }

    internal fun writeRequest(process: Process, request: SpeechWorkerRequest) {
        process.outputStream.use { output ->
            val input = if (request.operation == "conversion") {
                applio.conversionInput()
            } else {
                json.encodeToString(request).toByteArray(Charsets.UTF_8)
            }
            output.write(input)
        }
    }

    internal fun <T> run(builder: ProcessBuilder, action: (Process) -> T): T {
        val process = synchronized(this) {
            if (closed || Thread.currentThread().isInterrupted) {
                throw InterruptedException()
            }
            val started = builder.start()
            active.add(started)
            started
        }
        try {
            return action(process)
        } finally {
            try {
                stop(process)
            } finally {
                active.remove(process)
            }
        }
    }

    override fun close() {
        val processes = synchronized(this) {
            closed = true
            active.stream().toList()
        }
        processes.stream().forEach(::stop)
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
