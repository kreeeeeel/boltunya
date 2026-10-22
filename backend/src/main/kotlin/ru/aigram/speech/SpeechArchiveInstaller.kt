package ru.aigram.speech

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.springframework.stereotype.Component
import ru.aigram.domain.speech.SpeechAssetKind
import ru.aigram.domain.speech.SpeechEngineKind
import ru.aigram.exception.impl.SpeechInstallFailedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Collections
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.stream.Stream
import java.util.zip.CRC32

private const val MAX_ARCHIVE_ENTRIES = 4096
private const val MAX_EXTRACTED_BYTES = 512L * 1024 * 1024
private const val COPY_BUFFER_BYTES = 65536
private const val MAX_RUNTIME_ENTRIES = 50000
private const val MAX_RUNTIME_EXPANDED_BYTES = 12L * 1024 * 1024 * 1024
private val RESERVED_ZIP_NAMES = setOf("CON", "PRN", "AUX", "NUL")

@Component
class SpeechArchiveInstaller {
    fun extract(archive: Path, target: Path, definition: SpeechAssetDefinition) {
        Files.createDirectories(target)
        if (definition.asset.engine == SpeechEngineKind.APPLIO_RVC) {
            extractZip(archive, target, definition)
            return
        }
        if (definition.archiveRoot.isEmpty()) {
            Files.copy(archive, target.resolve("native.jar"), StandardCopyOption.REPLACE_EXISTING)
            return
        }
        var count = 0
        var bytes = 0L
        SpeechCancellableInputStream(Files.newInputStream(archive)).buffered(COPY_BUFFER_BYTES).use { input ->
            TarArchiveInputStream(BZip2CompressorInputStream(input)).use { tar ->
                Stream.iterate(tar.nextEntry, { it != null }, { tar.nextEntry }).forEach { entry ->
                    if (Thread.currentThread().isInterrupted) {
                        throw CancellationException()
                    }
                    if (entry != null) {
                        count++
                        val name = entry.name
                        val normalized = target.resolve(name).normalize()
                        if (count > MAX_ARCHIVE_ENTRIES || entry.size < 0 ||
                            entry.size > MAX_EXTRACTED_BYTES - bytes ||
                            !normalized.startsWith(target) || name.contains('\\') || name.contains(':') ||
                            name.startsWith('/') ||
                            !(name == definition.archiveRoot || name.startsWith("${definition.archiveRoot}/")) ||
                            entry.isSymbolicLink || entry.isLink ||
                            (!entry.isDirectory && !entry.isFile)
                        ) {
                            throw SpeechInstallFailedException()
                        }
                        bytes += entry.size
                        val relative = name.removePrefix(definition.archiveRoot).removePrefix("/")
                        val selected = definition.requiredFiles.stream().anyMatch {
                            relative == it || relative.startsWith("$it/")
                        } || relative == "MODEL_CARD" || relative.startsWith("LICENSE") ||
                            relative.startsWith("COPYING") || relative == "README" || relative == "README.md"
                        if (selected && entry.isFile) {
                            val destination = target.resolve(relative).normalize()
                            if (!destination.startsWith(target)) {
                                throw SpeechInstallFailedException()
                            }
                            Files.createDirectories(destination.parent)
                            Files.newOutputStream(destination).use { output ->
                                val buffer = ByteArray(COPY_BUFFER_BYTES)
                                var written = 0L
                                Stream.generate { tar.read(buffer) }.takeWhile { it >= 0 }.forEach { size ->
                                    if (Thread.currentThread().isInterrupted) {
                                        throw CancellationException()
                                    }
                                    if (size.toLong() > entry.size - written) {
                                        throw SpeechInstallFailedException()
                                    }
                                    written += size
                                    output.write(buffer, 0, size)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (definition.requiredFiles.stream().anyMatch { !Files.exists(target.resolve(it)) }) {
            throw SpeechInstallFailedException()
        }
    }

    private fun extractZip(archive: Path, target: Path, definition: SpeechAssetDefinition) {
        val runtime = definition.asset.kind == SpeechAssetKind.RUNTIME
        val maxEntries = if (runtime) {
            MAX_RUNTIME_ENTRIES
        } else {
            MAX_ARCHIVE_ENTRIES
        }
        val maxBytes = if (runtime) {
            MAX_RUNTIME_EXPANDED_BYTES
        } else {
            MAX_EXTRACTED_BYTES
        }
        var expanded = 0L
        val names = HashSet<String>()
        ZipFile.builder().setPath(archive).get().use { zip ->
            val entries = Collections.list(zip.entries)
            if (entries.size > maxEntries) {
                throw SpeechInstallFailedException()
            }
            entries.stream().forEach { entry ->
                if (Thread.currentThread().isInterrupted) {
                    throw CancellationException()
                }
                val name = entry.name.removeSuffix("/")
                val parts = name.split('/')
                val destination = target.resolve(name).normalize()
                if (parts.size > 48 || parts.stream().anyMatch { !zipBasename(it) } ||
                    !destination.startsWith(target) || entry.isUnixSymlink ||
                    !zip.canReadEntryData(entry) || entry.size < 0 || entry.size > maxBytes - expanded ||
                    !names.add(name.lowercase(Locale.ROOT))
                ) {
                    throw SpeechInstallFailedException()
                }
                expanded += entry.size
                val selected = definition.requiredFiles.stream().anyMatch { name == it || name.startsWith("$it/") } ||
                    name.startsWith("LICENSE") || name.startsWith("COPYING") || name == "README.md" ||
                    !name.contains('/') && name.endsWith(".txt")
                if (selected && !entry.isDirectory) {
                    Files.createDirectories(destination.parent)
                    val crc = CRC32()
                    var written = 0L
                    SpeechCancellableInputStream(zip.getInputStream(entry)).use { input ->
                        Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW).use { output ->
                            val buffer = ByteArray(COPY_BUFFER_BYTES)
                            Stream.generate { input.read(buffer) }.takeWhile { it >= 0 }.forEach { count ->
                                if (count.toLong() > entry.size - written) {
                                    throw SpeechInstallFailedException()
                                }
                                written += count
                                crc.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    if (written != entry.size || crc.value != entry.crc) {
                        throw SpeechInstallFailedException()
                    }
                }
            }
        }
        if (definition.requiredFiles.stream().anyMatch { !Files.exists(target.resolve(it)) }) {
            throw SpeechInstallFailedException()
        }
    }

    private fun zipBasename(value: String): Boolean {
        if (value.isEmpty() || value.length > 255 || value == "." || value == ".." ||
            value.endsWith('.') || value.endsWith(' ') || value.chars().anyMatch {
                it < 32 || it == 127 || it.toChar() in "\\:*?\"<>|"
            }
        ) {
            return false
        }
        val stem = value.substringBefore('.').uppercase(Locale.ROOT)
        return stem !in RESERVED_ZIP_NAMES &&
            !(stem.length == 4 && (stem.startsWith("COM") || stem.startsWith("LPT")) && stem[3] in '1'..'9')
    }
}
