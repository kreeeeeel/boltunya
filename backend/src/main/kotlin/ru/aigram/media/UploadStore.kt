package ru.aigram.media

import org.springframework.stereotype.Component
import ru.aigram.domain.media.OutgoingMedia
import ru.aigram.domain.media.OutgoingMediaKind
import ru.aigram.domain.media.StoredUpload
import ru.aigram.domain.media.UploadedFile
import ru.aigram.exception.impl.InvalidUploadException
import ru.aigram.exception.impl.MediaTooLargeException
import ru.aigram.exception.impl.UploadLimitReachedException
import ru.aigram.exception.impl.UploadNotFoundException
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.time.Clock
import java.util.Locale
import java.util.UUID
import java.util.stream.Collectors
import java.util.stream.IntStream

private const val MAX_UPLOAD_BYTES = 100L * 1024 * 1024
private const val MAX_PHOTO_BYTES = 10L * 1024 * 1024
private const val MAX_PENDING_UPLOADS = 20
private const val MAX_PENDING_BYTES = 500L * 1024 * 1024
private const val UPLOAD_LIFETIME_MS = 60L * 60 * 1000
private const val UPLOAD_DIRECTORY_DEPTH = 2
private const val MAX_FILES_PER_MESSAGE = 10
private const val MAX_CAPTION_CHARACTERS = 1024
private const val MAX_FILE_NAME_CHARACTERS = 200
private val PHOTO_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
private val VIDEO_MIME_TYPES = setOf("video/mp4", "video/webm")
private val MIME_TYPES = PHOTO_MIME_TYPES + VIDEO_MIME_TYPES + setOf("image/gif", "application/octet-stream")
private val FORBIDDEN_FILE_CHARACTERS = "\\/:*?\"<>|".chars().toArray().toSet()
private val RESERVED_FILE_NAMES = setOf("CON", "PRN", "AUX", "NUL")

@Component
class UploadStore(directory: Path, private val clock: Clock) {
    private val root = directory.resolve("uploads")
    private val entries = linkedMapOf<String, StoredUpload>()

    @Synchronized
    fun create(
        ownerId: String,
        name: String,
        mimeType: String,
        input: InputStream,
        expectedLength: Long,
    ): UploadedFile {
        cleanup()
        if (ownerId.isBlank()) {
            throw InvalidUploadException()
        }
        if (expectedLength > MAX_UPLOAD_BYTES) {
            throw MediaTooLargeException()
        }

        val pendingBytes = entries.values.stream().mapToLong { it.file.size }.sum()
        if (entries.size >= MAX_PENDING_UPLOADS || pendingBytes >= MAX_PENDING_BYTES ||
            expectedLength > MAX_PENDING_BYTES - pendingBytes
        ) {
            throw UploadLimitReachedException()
        }

        val fileName = safeFileName(name)
        val type = mimeType.substringBefore(';').lowercase().takeIf { it in MIME_TYPES } ?: "application/octet-stream"
        val id = UUID.randomUUID().toString()
        Files.createDirectories(root)
        val uploadDirectory = Files.createDirectory(root.resolve(id))
        val path = uploadDirectory.resolve(fileName)
        var completed = false

        try {
            val size = UploadInputStream(input, minOf(MAX_UPLOAD_BYTES, MAX_PENDING_BYTES - pendingBytes)).use {
                Files.copy(it, path)
            }
            if (size == 0L || expectedLength >= 0 && size != expectedLength) {
                throw InvalidUploadException()
            }

            val file = UploadedFile(id, fileName, type, size)
            entries[id] = StoredUpload(ownerId, file, path, clock.millis())
            completed = true
            return file
        } finally {
            if (!completed) {
                Files.deleteIfExists(path)
                Files.deleteIfExists(uploadDirectory)
            }
        }
    }

    private fun safeFileName(value: String): String {
        val name = value.codePoints().mapToObj { code ->
            if (Character.isISOControl(code) || Character.getType(code) == Character.FORMAT.toInt() ||
                code in FORBIDDEN_FILE_CHARACTERS
            ) {
                "_"
            } else {
                String(Character.toChars(code))
            }
        }.collect(Collectors.joining()).trim().trimEnd('.').take(MAX_FILE_NAME_CHARACTERS).ifBlank { "file" }

        val stem = name.substringBefore('.').trimEnd(' ', '.').uppercase(Locale.ROOT)
        if (stem in RESERVED_FILE_NAMES ||
            stem.length == 4 && (stem.startsWith("COM") || stem.startsWith("LPT")) && stem[3] in '1'..'9'
        ) {
            throw InvalidUploadException()
        }

        return name
    }

    @Synchronized
    fun media(ownerId: String, id: String, kind: OutgoingMediaKind, caption: String): OutgoingMedia {
        cleanup()
        val record = ownedRecord(ownerId, id)
        return resolve(record, kind, caption)
    }

    @Synchronized
    fun begin(ownerId: String, ids: List<String>, kind: OutgoingMediaKind, caption: String): List<OutgoingMedia> {
        if (ids.size !in 1..MAX_FILES_PER_MESSAGE || ids.stream().distinct().count() != ids.size.toLong() ||
            caption.length > MAX_CAPTION_CHARACTERS
        ) {
            throw InvalidUploadException()
        }

        cleanup()
        val records = ids.stream().map { id ->
            val record = ownedRecord(ownerId, id)
            if (record.inUse || record.discardRequested) {
                throw InvalidUploadException()
            }

            record
        }.toList()

        val media = IntStream.range(0, records.size).mapToObj {
            val currentCaption = if (it == 0) {
                caption
            } else {
                ""
            }
            resolve(records[it], kind, currentCaption)
        }.toList()

        records.stream().forEach { entries[it.file.id] = it.copy(inUse = true) }
        return media
    }

    @Synchronized
    fun release(ownerId: String, ids: List<String>, success: Boolean) {
        val records = ids.stream().distinct().map { entries[it] }.filter {
            it != null && it.ownerId == ownerId &&
                it.inUse
        }
            .map { it as StoredUpload }.toList()

        records.stream().forEach {
            entries[it.file.id] =
                it.copy(inUse = false, discardRequested = it.discardRequested || success)
        }

        val failures = records.stream()
            .filter { success || it.discardRequested }
            .map(::discardFailure)
            .filter { it != null }
            .map { it as IOException }
            .toList()

        if (failures.isNotEmpty()) {
            val exception = failures.first()
            failures.stream().skip(1).forEach(exception::addSuppressed)
            throw exception
        }
    }

    private fun resolve(record: StoredUpload, kind: OutgoingMediaKind, caption: String): OutgoingMedia {
        if (!Files.isRegularFile(record.path, LinkOption.NOFOLLOW_LINKS)) {
            throw UploadNotFoundException()
        }

        val resolved = if (kind == OutgoingMediaKind.MEDIA) {
            when (record.file.mimeType) {
                in PHOTO_MIME_TYPES -> OutgoingMediaKind.PHOTO
                in VIDEO_MIME_TYPES -> OutgoingMediaKind.VIDEO
                else -> throw InvalidUploadException()
            }
        } else {
            kind
        }

        when (resolved) {
            OutgoingMediaKind.MEDIA -> throw InvalidUploadException()
            OutgoingMediaKind.PHOTO -> {
                if (record.file.mimeType !in PHOTO_MIME_TYPES || record.file.size > MAX_PHOTO_BYTES) {
                    throw InvalidUploadException()
                }
            }
            OutgoingMediaKind.VIDEO -> {
                if (record.file.mimeType !in VIDEO_MIME_TYPES) {
                    throw InvalidUploadException()
                }
            }
            OutgoingMediaKind.DOCUMENT -> Unit
        }

        return OutgoingMedia(record.path, resolved, caption)
    }

    @Synchronized
    fun remove(ownerId: String, id: String) {
        val record = entries[id]
        if (record == null || record.ownerId != ownerId) {
            return
        }
        if (record.inUse) {
            entries[id] = record.copy(discardRequested = true)
            return
        }

        Files.deleteIfExists(record.path)
        Files.deleteIfExists(record.path.parent)
        entries.remove(id)
    }

    private fun ownedRecord(ownerId: String, id: String): StoredUpload {
        val record = entries[id]
        if (record == null || record.ownerId != ownerId) {
            throw UploadNotFoundException()
        }

        return record
    }

    private fun discardFailure(record: StoredUpload): IOException? = try {
        remove(record.ownerId, record.file.id)
        null
    } catch (exception: IOException) {
        exception
    }

    private fun cleanup() {
        val before = clock.millis() - UPLOAD_LIFETIME_MS
        val expired = entries.values.stream()
            .filter { !it.inUse && (it.discardRequested || it.createdAt < before) }
            .toList()
        expired.stream().forEach { remove(it.ownerId, it.file.id) }

        val tracked = entries.values.stream().map { it.path }.collect(Collectors.toSet())
        if (Files.isDirectory(root)) {
            Files.walk(root, UPLOAD_DIRECTORY_DEPTH).use { paths ->
                paths.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
                    .filter { it !in tracked && Files.getLastModifiedTime(it).toMillis() < before }
                    .forEach { Files.deleteIfExists(it) }
            }
            Files.list(root).use { paths ->
                paths.filter { Files.isDirectory(it, LinkOption.NOFOLLOW_LINKS) }
                    .filter { Files.list(it).use { children -> children.findAny().isEmpty } }
                    .forEach { Files.deleteIfExists(it) }
            }
        }
    }
}
