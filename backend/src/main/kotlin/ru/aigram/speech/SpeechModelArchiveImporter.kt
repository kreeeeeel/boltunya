package ru.aigram.speech

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.springframework.stereotype.Component
import ru.aigram.domain.speech.SpeechImportedFiles
import ru.aigram.exception.impl.InvalidSpeechModelImportException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.stream.Stream

private const val MAX_IMPORTED_ARCHIVE_BYTES = 100L * 1024 * 1024
private const val MAX_IMPORTED_EXPANDED_BYTES = 512L * 1024 * 1024
private const val MAX_IMPORTED_ENTRIES = 4096
private const val IMPORT_BUFFER_BYTES = 65536
private val EXECUTABLE_EXTENSIONS = setOf("exe", "dll", "com", "bat", "cmd", "ps1", "jar", "so", "dylib")
private val RESERVED_NAMES = setOf("CON", "PRN", "AUX", "NUL")

@Component
class SpeechModelArchiveImporter {
    fun extract(archive: Path, staging: Path): SpeechImportedFiles {
        if (!Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS) ||
            Files.size(archive) !in 1..MAX_IMPORTED_ARCHIVE_BYTES ||
            !Files.isDirectory(staging, LinkOption.NOFOLLOW_LINKS)
        ) {
            throw InvalidSpeechModelImportException()
        }
        var root = ""
        var model = ""
        var entries = 0
        var expanded = 0L
        val names = HashSet<String>()
        SpeechCancellableInputStream(Files.newInputStream(archive)).buffered(IMPORT_BUFFER_BYTES).use { source ->
            TarArchiveInputStream(BZip2CompressorInputStream(source)).use { tar ->
                Stream.iterate(tar.nextEntry, { it != null }, { tar.nextEntry }).forEach { entry ->
                    if (Thread.currentThread().isInterrupted) {
                        throw CancellationException()
                    }
                    if (entry != null) {
                        entries++
                        val name = entry.name.removeSuffix("/")
                        val segments = name.split('/')
                        if (entries > MAX_IMPORTED_ENTRIES || entry.size < 0 ||
                            entry.size > MAX_IMPORTED_EXPANDED_BYTES - expanded || segments.size > 32 ||
                            segments.stream().anyMatch { !basename(it) } ||
                            entry.isLink || entry.isSymbolicLink || entry.isSparse ||
                            (!entry.isDirectory && !entry.isFile) ||
                            !names.add(name.lowercase(Locale.ROOT))
                        ) {
                            throw InvalidSpeechModelImportException()
                        }
                        expanded += entry.size
                        if (root.isEmpty()) {
                            root = segments[0]
                        }
                        if (segments[0] != root || segments.size == 1 && !entry.isDirectory) {
                            throw InvalidSpeechModelImportException()
                        }
                        val relative = name.substringAfter('/', "")
                        if (relative.isNotEmpty()) {
                            if (!allowed(relative, entry.isDirectory)) {
                                throw InvalidSpeechModelImportException()
                            }
                            val target = staging.resolve(relative).normalize()
                            if (!target.startsWith(staging)) {
                                throw InvalidSpeechModelImportException()
                            }
                            if (entry.isDirectory) {
                                Files.createDirectories(target)
                            } else {
                                if (relative.endsWith(".onnx", ignoreCase = true)) {
                                    if (model.isNotEmpty() || relative.contains('/')) {
                                        throw InvalidSpeechModelImportException()
                                    }
                                    model = relative
                                }
                                Files.createDirectories(target.parent)
                                Files.newOutputStream(
                                    target,
                                    StandardOpenOption.CREATE_NEW,
                                    StandardOpenOption.WRITE,
                                ).use { output ->
                                    val buffer = ByteArray(IMPORT_BUFFER_BYTES)
                                    var written = 0L
                                    Stream.generate { tar.read(buffer) }.takeWhile { it >= 0 }.forEach { count ->
                                        if (Thread.currentThread().isInterrupted) {
                                            throw CancellationException()
                                        }
                                        if (count.toLong() > entry.size - written) {
                                            throw InvalidSpeechModelImportException()
                                        }
                                        written += count
                                        output.write(buffer, 0, count)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        val required = listOf(
            model,
            "tokens.txt",
            "espeak-ng-data/phondata",
            "espeak-ng-data/phonindex",
            "espeak-ng-data/phontab",
        )
        if (model.isEmpty() || required.stream().anyMatch {
                val path = staging.resolve(it)
                !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) == 0L
            }
        ) {
            throw InvalidSpeechModelImportException()
        }
        return SpeechImportedFiles(model, required)
    }

    private fun allowed(relative: String, directory: Boolean): Boolean {
        val lower = relative.lowercase(Locale.ROOT)
        if (lower.substringAfterLast('.', "") in EXECUTABLE_EXTENSIONS) {
            return false
        }
        if (relative == "espeak-ng-data" || relative.startsWith("espeak-ng-data/") ||
            relative == "legal" || relative.startsWith("legal/") ||
            relative == "licenses" || relative.startsWith("licenses/")
        ) {
            return true
        }
        if (relative.contains('/') || directory) {
            return false
        }
        return relative == "tokens.txt" || lower.endsWith(".onnx") || lower.endsWith(".onnx.json") ||
            lower.startsWith("license") || lower.startsWith("copying") || lower.startsWith("model_card") ||
            lower.startsWith("readme")
    }

    private fun basename(value: String): Boolean {
        if (value.isEmpty() || value.length > 200 || value.startsWith(' ') || value.endsWith(' ') ||
            value.startsWith('.') || value.endsWith('.')
        ) {
            return false
        }
        val stem = value.substringBefore('.').trimEnd(' ').uppercase(Locale.ROOT)
        if (stem in RESERVED_NAMES || stem.length == 4 &&
            (stem.startsWith("COM") || stem.startsWith("LPT")) && stem[3] in '1'..'9'
        ) {
            return false
        }
        return value.chars().allMatch {
            it in 'a'.code..'z'.code || it in 'A'.code..'Z'.code || it in '0'.code..'9'.code ||
                it == '.'.code || it == '-'.code || it == '_'.code || it == '!'.code || it == '+'.code ||
                it == ' '.code
        }
    }
}
