package ru.aigram.telegram

import it.tdlight.jni.TdApi
import ru.aigram.domain.media.MediaAttachment
import ru.aigram.domain.media.MediaFile
import ru.aigram.exception.impl.MediaNotReadyException
import ru.aigram.exception.impl.MediaTooLargeException
import ru.aigram.exception.impl.MediaUnavailableException
import ru.aigram.exception.impl.UnknownMediaException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private const val MAX_FILE_BYTES = 100L * 1024 * 1024
private val SUPPORTED_MIME_TYPES = setOf(
    "image/jpeg", "image/png", "image/webp", "image/gif", "video/mp4", "video/webm",
    "audio/ogg", "audio/mpeg", "audio/mp4", "audio/wav", "audio/flac", "application/x-tgsticker",
    "application/x-tgwallpattern", "image/svg+xml",
)

class TelegramMediaStore(
    private val directory: Path,
    private val cacheDirectories: List<Path> = emptyList(),
    private val outgoingFiles: TelegramOutgoingFileStore? = null,
    private val sessionVersion: () -> Long = { 0 },
    private val download: (Int) -> TdApi.File,
) {
    private val files = ConcurrentHashMap<Int, MediaAttachment>()
    private val downloaded = ConcurrentHashMap<Int, Boolean>()
    private val revision = AtomicLong()
    private val recovered = ConcurrentHashMap<Triple<Long, Int, String>, Path>()

    fun isDownloaded(id: Int): Boolean = downloaded[id] == true
    fun uniqueId(id: Int): String = files[id]?.uniqueId.orEmpty()
    fun conflicts(media: MediaAttachment): Boolean =
        media.uniqueId.isNotEmpty() && uniqueId(media.fileId).isNotEmpty() &&
            media.uniqueId != uniqueId(media.fileId)
    fun version(): Long = revision.get()
    fun update(file: TdApi.File) {
        downloaded[file.id] = file.local?.isDownloadingCompleted == true
    }
    fun remember(media: MediaAttachment) {
        files.putIfAbsent(media.fileId, media)
    }
    fun invalidate() {
        downloaded.replaceAll { _, _ -> false }
        recovered.clear()
        revision.incrementAndGet()
    }

    fun register(file: TdApi.File, mimeType: String, fileName: String): Int {
        downloaded[file.id] = file.local?.isDownloadingCompleted == true
        files[file.id] = MediaAttachment(
            fileId = file.id,
            mimeType = mimeType.takeIf { it in SUPPORTED_MIME_TYPES } ?: "application/octet-stream",
            fileName = fileName,
            size = maxOf(file.size, file.expectedSize),
            uniqueId = file.remote?.uniqueId.orEmpty(),
        )
        return file.id
    }

    fun get(fileId: Int): MediaFile {
        val version = sessionVersion()
        val metadata = files[fileId]

        if (metadata == null) {
            throw UnknownMediaException()
        }

        if (metadata.size > MAX_FILE_BYTES) {
            throw MediaTooLargeException()
        }
        val file = download(fileId)
        if (sessionVersion() != version || file.id != fileId || metadata.uniqueId.isNotEmpty() &&
            file.remote?.uniqueId != metadata.uniqueId
        ) {
            throw MediaUnavailableException()
        }
        update(file)
        if (file.size > MAX_FILE_BYTES || file.expectedSize > MAX_FILE_BYTES) {
            throw MediaTooLargeException()
        }
        val local = file.local
        if (local == null || !local.isDownloadingCompleted || local.path.isBlank()) {
            throw MediaNotReadyException()
        }
        val original = Path.of(local.path)
        if (!Files.isRegularFile(original)) {
            throw MediaUnavailableException()
        }
        var path = original.toRealPath()
        val roots = (listOf(directory) + cacheDirectories).filter(Files::isDirectory).map { it.toRealPath() }
        if (roots.none(path::startsWith) && metadata.mimeType == "audio/ogg" &&
            metadata.fileName == "voice.ogg" && metadata.uniqueId.isNotEmpty()
        ) {
            val retained = outgoingFiles
            if (retained != null) {
                val copy = recovered.compute(Triple(version, fileId, metadata.uniqueId)) { _, previous ->
                    if (previous != null && Files.isRegularFile(previous)) {
                        previous
                    } else {
                        retained.recoverVoice(original)
                    }
                }
                if (copy == null) {
                    throw MediaUnavailableException()
                }
                path = copy.toRealPath()
            }
        }
        val allowedRoots = (listOf(directory) + cacheDirectories).filter(Files::isDirectory).map { it.toRealPath() }
        if (sessionVersion() != version || allowedRoots.none(path::startsWith) || !Files.isRegularFile(path) ||
            Files.size(path) > MAX_FILE_BYTES
        ) {
            throw MediaUnavailableException()
        }
        return MediaFile(path, metadata.mimeType, metadata.fileName)
    }

    fun clear() {
        files.clear()
        downloaded.clear()
        recovered.clear()
    }
}
