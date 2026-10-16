package ru.boltunya.telegram

import it.tdlight.jni.TdApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.exception.InvalidInputException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class TelegramMediaStoreTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `serves only registered files inside Telegram download directory`() {
        val downloads = Files.createDirectories(directory.resolve("files"))
        val path = Files.write(downloads.resolve("photo.jpg"), byteArrayOf(1, 2, 3))
        val file = localFile(path)
        val store = TelegramMediaStore(downloads) { file }
        assertFailsWith<InvalidInputException> { store.get(1) }
        store.register(file, "image/jpeg", "photo.jpg")
        assertEquals(path.toRealPath(), store.get(1).path)
        assertEquals("image/jpeg", store.get(1).mimeType)
        store.register(file, "text/html", "document.html")
        assertEquals("application/octet-stream", store.get(1).mimeType)
        file.local.path = Files.write(directory.resolve("outside.jpg"), byteArrayOf(4)).toString()
        assertFailsWith<InvalidInputException> { store.get(1) }
        store.clear()
        assertFailsWith<InvalidInputException> { store.get(1) }
    }

    @Test
    fun `reads TDLib avatar and sticker caches without exposing the database directory`() {
        val downloads = Files.createDirectories(directory.resolve("files"))
        val cache = Files.createDirectories(directory.resolve("data/profile_photos"))
        val photo = Files.write(cache.resolve("photo.jpg"), byteArrayOf(1))
        val file = localFile(photo)
        val store = TelegramMediaStore(downloads, listOf(cache)) { file }
        store.register(file, "image/jpeg", "avatar.jpg")
        assertEquals(photo.toRealPath(), store.get(1).path)
        val database = Files.write(directory.resolve("data/database.bin"), byteArrayOf(2))
        file.local.path = database.toString()
        assertFailsWith<InvalidInputException> { store.get(1) }
    }

    @Test
    fun `rejects large files before downloading`() {
        var downloaded = false
        val file = TdApi.File().apply {
            id = 2
            expectedSize = 101L * 1024 * 1024
        }
        val store = TelegramMediaStore(directory) {
            downloaded = true
            file
        }
        store.register(file, "video/mp4", "video.mp4")
        assertFailsWith<InvalidInputException> { store.get(2) }
        assertFalse(downloaded)
    }

    private fun localFile(path: Path): TdApi.File = TdApi.File().apply {
        id = 1
        size = 3
        local = TdApi.LocalFile().apply {
            this.path = path.toString()
            isDownloadingCompleted = true
        }
    }
}
