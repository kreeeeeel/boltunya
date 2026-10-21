package ru.aigram.speech

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.springframework.stereotype.Component
import ru.aigram.exception.impl.SpeechInstallFailedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.CancellationException
import java.util.stream.Stream

private const val MAX_ARCHIVE_ENTRIES = 4096
private const val MAX_EXTRACTED_BYTES = 512L * 1024 * 1024
private const val COPY_BUFFER_BYTES = 65536

@Component
class SpeechArchiveInstaller {
    fun extract(archive: Path, target: Path, definition: SpeechAssetDefinition) {
        Files.createDirectories(target)
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
}
