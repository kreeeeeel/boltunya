package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.ChatMessage
import ru.aigram.domain.MediaAttachment
import ru.aigram.storage.SettingsStore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaDownloadPolicyTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `does not substitute a reused active file id for archived media from an older session`() {
        val files = TelegramMediaStore(directory) { error("No download expected") }
        files.register(
            TdApi.File().apply {
                id = 7
                remote = TdApi.RemoteFile().apply { uniqueId = "different-file" }
            },
            "image/jpeg",
            "new.jpg",
        )
        val archived =
            ChatMessage(
                "1",
                "42",
                "Caption",
                false,
                1,
                "photo",
                MediaAttachment(7, "image/jpeg", "original.jpg", uniqueId = "original-file"),
            )
        val decorated = MediaDownloadPolicy(SettingsStore(directory)).decorate(archived, files)
        assertEquals(null, decorated.media)
        assertEquals("Caption", decorated.text)
    }

    @Test
    fun `does not request an unverifiable archived thumbnail after deletion`() {
        val files = TelegramMediaStore(directory) { error("No download expected") }
        val message = ChatMessage(
            "1",
            "42",
            "Caption",
            false,
            1,
            "video",
            MediaAttachment(7, "video/mp4", "video.mp4", thumbnailFileId = 8, uniqueId = "original-file"),
        )
        val policy = MediaDownloadPolicy(SettingsStore(directory))
        assertEquals(8, policy.decorate(message, files).media?.thumbnailFileId)
        assertEquals(0, policy.decorate(message.copy(deleted = true), files).media?.thumbnailFileId)
    }

    @Test
    fun `respects individual media categories size limits and preserves explicit cached availability`() {
        val settings = SettingsStore(directory)
        val policy = MediaDownloadPolicy(settings)
        settings.update { it.copy(autoDownloadPhotos = false, autoDownloadVideos = true, autoDownloadMaxMb = 10) }
        assertFalse(policy.allows("photo", 1))
        assertTrue(policy.allows("video", 9L * 1024 * 1024))
        assertFalse(policy.allows("video", 11L * 1024 * 1024))
        assertFalse(policy.allows("document", 1))
        assertTrue(policy.allows("sticker", 1))
        val store = TelegramMediaStore(directory) { error("No download expected") }
        store.update(
            TdApi.File().apply {
                id = 1
                local = TdApi.LocalFile().apply { isDownloadingCompleted = true }
            },
        )
        val message = ChatMessage("1", "42", "", false, 1, "photo", MediaAttachment(1, "image/jpeg", "photo.jpg"))
        assertTrue(policy.decorate(message, store).media?.downloaded == true)
        store.invalidate()
        assertFalse(policy.decorate(message, store).media?.downloaded == true)
        assertEquals(1L, policy.decorate(message, store).media?.cacheVersion)
    }

    @Test
    fun `prefetch queue deduplicates files and rechecks changed preferences before downloading`() {
        val settings = SettingsStore(directory)
        val policy = MediaDownloadPolicy(settings)
        var downloads = 0
        val path = Files.write(directory.resolve("photo.jpg"), byteArrayOf(1, 2, 3))
        val file = TdApi.File().apply {
            id = 1
            size = 3
            local =
                TdApi.LocalFile().apply {
                    this.path = path.toString()
                    isDownloadingCompleted = true
                }
        }
        val files = TelegramMediaStore(directory) {
            downloads++
            file
        }
        files.register(file, "image/jpeg", "photo.jpg")
        files.invalidate()
        val message =
            ChatMessage("1", "42", "", false, 1, "photo", MediaAttachment(1, "image/jpeg", "photo.jpg", size = 3))
        MediaPrefetcher(files, policy).use { prefetch ->
            prefetch.observe(message)
            prefetch.observe(message)
            settings.update { it.copy(autoDownloadPhotos = false) }
            prefetch.downloadOne()
            assertEquals(0, downloads)
            settings.update { it.copy(autoDownloadPhotos = true) }
            prefetch.observe(message)
            prefetch.observe(message)
            prefetch.downloadOne()
            prefetch.downloadOne()
            assertEquals(1, downloads)
        }
    }
}
