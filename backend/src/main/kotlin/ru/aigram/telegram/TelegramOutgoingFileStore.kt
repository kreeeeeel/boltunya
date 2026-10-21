package ru.aigram.telegram

import org.springframework.stereotype.Component
import ru.aigram.domain.system.CacheStatistics
import ru.aigram.exception.impl.MediaTooLargeException
import ru.aigram.exception.impl.MediaUnavailableException
import ru.aigram.exception.impl.UploadLimitReachedException
import ru.aigram.media.UploadInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Clock
import java.util.UUID
import java.util.concurrent.CompletableFuture

private const val MAX_OUTGOING_FILE_BYTES = 100L * 1024 * 1024
private const val MAX_OUTGOING_CACHE_BYTES = 500L * 1024 * 1024
private const val MAX_OUTGOING_CACHE_FILES = 1024
private const val OUTGOING_CACHE_LIFETIME_MS = 24L * 60 * 60 * 1000
private const val SENDING_MARKER = ".sending"

/** Owns native upload inputs independently of temporary drafts and staged attachments. */
@Component
class TelegramOutgoingFileStore(directory: Path, private val clock: Clock) {
    private val profile = directory.toAbsolutePath().normalize()
    private val root = directory.resolve("telegram/files/outgoing").toAbsolutePath().normalize()
    private val legacyVoices = directory.resolve("speech/generated").toAbsolutePath().normalize()

    @Synchronized
    fun retain(source: Path): Path = copy(source, true)

    @Synchronized
    fun recoverVoice(source: Path): Path {
        if (!Files.isDirectory(legacyVoices, LinkOption.NOFOLLOW_LINKS) ||
            !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) ||
            legacyVoices.toRealPath() != profile.toRealPath().resolve("speech/generated") ||
            source.toRealPath().parent != legacyVoices.toRealPath() ||
            source.fileName.toString().substringAfterLast('.', "") != "ogg"
        ) {
            throw MediaUnavailableException()
        }
        val header = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS).use { it.readNBytes(4) }
        if (!header.contentEquals("OggS".toByteArray(Charsets.US_ASCII))) {
            throw MediaUnavailableException()
        }
        return copy(source, false)
    }

    /** A timeout is not a terminal result; callers invoke this from the native completion callback. */
    @Synchronized
    fun completed(path: Path) {
        try {
            val entry = owned(path)
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                Files.setLastModifiedTime(path, FileTime.fromMillis(clock.millis()))
            }
            Files.deleteIfExists(entry.resolve(SENDING_MARKER))
        } catch (exception: IOException) {
            System.err.println("Outgoing media completion cleanup failed: ${exception.javaClass.simpleName}")
        }
    }

    fun track(paths: List<Path>, delivered: CompletableFuture<*>) {
        delivered.whenComplete { _, error ->
            // Cancellation, partial acceptance and connection shutdown do not prove native upload termination.
            if (error == null) {
                paths.stream().forEach(::completed)
            }
        }
    }

    @Synchronized
    fun statistics(): CacheStatistics {
        val payloads = entries().stream().flatMap { entry ->
            Files.list(entry).filter {
                it.fileName.toString() != SENDING_MARKER && Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS)
            }
        }.toList()
        val bytes = payloads.stream().mapToLong(Files::size).sum()
        return CacheStatistics(totalBytes = bytes, fileCount = payloads.size.toLong(), documentBytes = bytes)
    }

    @Synchronized
    fun clearCompleted() {
        entries().stream().filter { !Files.exists(it.resolve(SENDING_MARKER), LinkOption.NOFOLLOW_LINKS) }
            .forEach(::remove)
    }

    private fun copy(source: Path, pinned: Boolean): Path {
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw MediaUnavailableException()
        }
        if (source.fileName.toString() == SENDING_MARKER) {
            throw MediaUnavailableException()
        }
        val size = Files.size(source)
        if (size > MAX_OUTGOING_FILE_BYTES) {
            throw MediaTooLargeException()
        }
        val before = clock.millis() - OUTGOING_CACHE_LIFETIME_MS
        val current = entries()
        current.stream().filter { !Files.exists(it.resolve(SENDING_MARKER), LinkOption.NOFOLLOW_LINKS) }
            .filter { entry ->
                Files.list(entry).use { files ->
                    files.allMatch { Files.getLastModifiedTime(it, LinkOption.NOFOLLOW_LINKS).toMillis() < before }
                }
            }.forEach(::remove)
        val retained = entries()
        val used = retained.stream().mapToLong { entry ->
            Files.list(entry).use { files ->
                files.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }.mapToLong(Files::size).sum()
            }
        }.sum()
        if (retained.size >= MAX_OUTGOING_CACHE_FILES || size > MAX_OUTGOING_CACHE_BYTES - used) {
            throw UploadLimitReachedException()
        }
        Files.createDirectories(root)
        if (root.toRealPath() != profile.toRealPath().resolve("telegram/files/outgoing")) {
            throw MediaUnavailableException()
        }
        val entry = Files.createDirectory(root.resolve(UUID.randomUUID().toString()))
        val target = entry.resolve(source.fileName.toString())
        var complete = false
        try {
            if (pinned) {
                Files.createFile(entry.resolve(SENDING_MARKER))
            }
            Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS).use { input ->
                UploadInputStream(input, minOf(MAX_OUTGOING_FILE_BYTES, MAX_OUTGOING_CACHE_BYTES - used)).use {
                    Files.copy(it, target)
                }
            }
            Files.setLastModifiedTime(target, FileTime.fromMillis(clock.millis()))
            complete = true
            return target
        } finally {
            if (!complete) {
                remove(entry)
            }
        }
    }

    private fun owned(path: Path): Path {
        val entry = path.toAbsolutePath().normalize().parent
        if (entry == null || entry.parent != root || !Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS) ||
            entry.toRealPath().parent != root.toRealPath()
        ) {
            throw MediaUnavailableException()
        }
        return entry
    }

    private fun entries(): List<Path> {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return emptyList()
        }
        if (root.toRealPath() != profile.toRealPath().resolve("telegram/files/outgoing")) {
            throw MediaUnavailableException()
        }
        return Files.list(root).use { paths ->
            paths.filter { Files.isDirectory(it, LinkOption.NOFOLLOW_LINKS) }
                .filter { it.toRealPath().parent == root.toRealPath() }.toList()
        }
    }

    private fun remove(entry: Path) {
        if (entry.parent != root || !Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS) ||
            entry.toRealPath().parent != root.toRealPath()
        ) {
            throw MediaUnavailableException()
        }
        Files.list(entry).use { files -> files.forEach(Files::delete) }
        Files.delete(entry)
    }
}
