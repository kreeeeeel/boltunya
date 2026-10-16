package ru.boltunya.ai

import jakarta.inject.Inject
import ru.boltunya.exception.IntegrationException
import ru.boltunya.exception.impl.IncompleteModelDownloadException
import ru.boltunya.exception.impl.InvalidModelRangeException
import ru.boltunya.exception.impl.InvalidModelSizeException
import ru.boltunya.exception.impl.ModelBlobDownloadFailedException
import ru.boltunya.exception.impl.ModelChecksumMismatchException
import ru.boltunya.exception.impl.ModelLocationMissingException
import ru.boltunya.exception.impl.ModelRangeDownloadFailedException
import ru.boltunya.exception.impl.ModelWriteFailedException
import java.net.HttpURLConnection
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.stream.LongStream

private const val CHUNK_BYTES = 4L * 1024 * 1024

class RegistryBlobDownloader @Inject constructor(private val http: HttpClient) {
    fun download(url: URI, target: Path, digest: String, size: Long, onBytes: (Long) -> Unit) {
        if (size <= 0) {
            throw InvalidModelSizeException()
        }
        val source = resolve(url)
        val temporary = target.resolveSibling("${target.fileName}.chunks.partial")
        FileChannel.open(
            temporary,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        ).use { output ->
            Executors.newFixedThreadPool(16).use { executor ->
                val jobs = LongStream.range(0, (size + CHUNK_BYTES - 1) / CHUNK_BYTES).mapToObj { index ->
                    executor.submit {
                        val start = index * CHUNK_BYTES
                        val end = minOf(start + CHUNK_BYTES, size) - 1
                        val bytes = readChunk(source, start, end, size)
                        val buffer = ByteBuffer.wrap(bytes)
                        var position = start
                        generateSequence {
                            if (buffer.hasRemaining()) output.write(buffer, position) else null
                        }.forEach { written ->
                            if (written <= 0) throw ModelWriteFailedException()
                            position += written
                        }
                        synchronized(onBytes) { onBytes(bytes.size.toLong()) }
                    }
                }.toList()
                try {
                    jobs.forEach { it.get() }
                } catch (exception: Exception) {
                    jobs.forEach { it.cancel(true) }
                    executor.shutdownNow()
                    throw ModelBlobDownloadFailedException(exception)
                }
            }
        }
        val checksum = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(temporary).use { input ->
            val buffer = ByteArray(1024 * 1024)
            generateSequence { input.read(buffer).takeIf { it >= 0 } }.forEach { checksum.update(buffer, 0, it) }
        }
        if (Files.size(temporary) != size ||
            HexFormat.of().formatHex(checksum.digest()) != digest.substringAfter(':')
        ) {
            throw ModelChecksumMismatchException()
        }
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun resolve(url: URI): URI {
        val request = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(30)).build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
        response.body().close()
        if (response.statusCode() == 200) return url
        val location = response.headers().firstValue("Location").orElse("")
        if (response.statusCode() !in setOf(301, 302, 303, 307, 308) || location.isBlank()) {
            throw ModelLocationMissingException()
        }
        return url.resolve(location)
    }

    private fun readChunk(url: URI, start: Long, end: Long, size: Long, attempt: Int = 0): ByteArray {
        try {
            val curl = Path.of(System.getenv("SystemRoot") ?: "C:/Windows", "System32/curl.exe")
            if (Files.isExecutable(curl)) {
                return readWindowsChunk(curl, url, start, end)
            }
            val connection = url.toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Range", "bytes=$start-$end")
            try {
                val range = connection.getHeaderField("Content-Range")
                val partial = connection.responseCode == 206 && range == "bytes $start-$end/$size"
                val complete = connection.responseCode == 200 && start == 0L && end == size - 1
                if (!partial && !complete) throw InvalidModelRangeException()
                return connection.inputStream.use { input ->
                    val expected = (end - start + 1).toInt()
                    val bytes = input.readNBytes(expected)
                    if (bytes.size != expected) throw IncompleteModelDownloadException()
                    bytes
                }
            } finally {
                connection.disconnect()
            }
        } catch (exception: Exception) {
            if (exception is InterruptedException || attempt >= 2) throw exception
            return readChunk(url, start, end, size, attempt + 1)
        }
    }

    private fun readWindowsChunk(curl: Path, url: URI, start: Long, end: Long): ByteArray {
        val process = ProcessBuilder(
            curl.toString(), "--fail", "--silent", "--show-error", "--location",
            "--connect-timeout", "15", "--max-time", "60", "--range", "$start-$end", url.toString(),
        ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        try {
            val expected = (end - start + 1).toInt()
            val bytes = process.inputStream.use { it.readNBytes(expected + 1) }
            if (bytes.size != expected) {
                throw IncompleteModelDownloadException()
            }
            if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw ModelRangeDownloadFailedException()
            }
            return bytes
        } finally {
            process.destroyForcibly()
        }
    }
}
