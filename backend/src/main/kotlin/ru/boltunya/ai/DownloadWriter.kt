package ru.boltunya.ai

import jakarta.inject.Inject
import ru.boltunya.exception.impl.IncompleteDownloadException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream

private const val BUFFER_BYTES = 64 * 1024

class DownloadWriter @Inject constructor() {
    fun copy(input: InputStream, target: Path, total: Long, onBytes: (Long, Long) -> Unit) {
        var received = 0L
        val buffer = ByteArray(BUFFER_BYTES)
        Files.newOutputStream(target).use { output ->
            Stream.generate { input.read(buffer) }.takeWhile { it >= 0 }.forEach { count ->
                output.write(buffer, 0, count)
                received += count
                onBytes(received, total)
            }
        }
        if (total > 0 && received != total) {
            throw IncompleteDownloadException()
        }
    }
}
