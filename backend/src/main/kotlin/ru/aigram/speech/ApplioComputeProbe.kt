package ru.aigram.speech

import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import ru.aigram.exception.impl.SpeechInferenceFailedException
import ru.aigram.exception.impl.SpeechTimeoutException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private const val PROBE_TIMEOUT_SECONDS = 30L
private const val MAX_COMPUTE_RESULT_BYTES = 4096L

@Component
class ApplioComputeProbe(
    directory: Path,
    private val json: Json,
    private val worker: ApplioSpeechWorker,
    private val processes: SpeechWorkerProcess,
) : SpeechComputeProbe {
    private val directory = directory.resolve("speech/work")
    private var active: CompletableFuture<SpeechComputeProbeResult>? = null

    override fun probe(runtime: Path): SpeechComputeProbeResult = coalesce { read(runtime) }

    internal fun coalesce(read: () -> SpeechComputeProbeResult): SpeechComputeProbeResult {
        val (future, owner) = synchronized(this) {
            val existing = active
            if (existing != null) {
                existing to false
            } else {
                val created = CompletableFuture<SpeechComputeProbeResult>()
                active = created
                created to true
            }
        }
        if (!owner) {
            try {
                return future.get(PROBE_TIMEOUT_SECONDS + 5, TimeUnit.SECONDS)
            } catch (exception: ExecutionException) {
                throw SpeechInferenceFailedException(exception.cause)
            } catch (exception: TimeoutException) {
                throw SpeechTimeoutException(exception)
            }
        }
        try {
            val result = read()
            future.complete(result)
            return result
        } catch (exception: Exception) {
            future.completeExceptionally(exception)
            throw exception
        } finally {
            synchronized(this) {
                if (active === future) {
                    active = null
                }
            }
        }
    }

    private fun read(runtime: Path): SpeechComputeProbeResult {
        Files.createDirectories(directory)
        val working = Files.createTempDirectory(directory, "compute-")
        try {
            val result = working.resolve("compute.json")
            return processes.run(worker.computeBuilder(runtime, working, result)) { process ->
                process.outputStream.use { it.write(worker.computeInput()) }
                if (!process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw SpeechTimeoutException()
                }
                if (process.exitValue() != 0 || !Files.isRegularFile(result, LinkOption.NOFOLLOW_LINKS) ||
                    Files.size(result) !in 1..MAX_COMPUTE_RESULT_BYTES
                ) {
                    throw SpeechInferenceFailedException()
                }
                val response = json.decodeFromString<SpeechComputeProbeResult>(Files.readString(result))
                if (response.gpuMemoryBytes < 0 || response.gpuName.length > 256 ||
                    response.torchVersion.length > 80 || response.cudaVersion.length > 80 ||
                    (response.cudaAvailable && (response.gpuName.isBlank() || response.gpuMemoryBytes == 0L))
                ) {
                    throw SpeechInferenceFailedException()
                }
                response
            }
        } finally {
            Files.walk(working).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }
}
