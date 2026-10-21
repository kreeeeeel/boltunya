package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.exception.InvalidInputException
import ru.aigram.exception.impl.MediaUnavailableException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    fun `serves TDLib wallpaper cache while rejecting adjacent database files`() {
        val downloads = Files.createDirectories(directory.resolve("files"))
        val cache = Files.createDirectories(directory.resolve("data/wallpapers"))
        val wallpaper = Files.write(cache.resolve("wallpaper.jpg"), byteArrayOf(1, 2, 3))
        val file = localFile(wallpaper)
        val store = TelegramMediaStore(downloads, listOf(cache)) { file }
        store.register(file, "image/jpeg", "wallpaper.jpg")
        assertEquals(wallpaper.toRealPath(), store.get(1).path)
        assertEquals("image/jpeg", store.get(1).mimeType)
        file.local.path = Files.write(directory.resolve("data/database.bin"), byteArrayOf(4)).toString()
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

    @Test
    fun `serves retained outgoing media after upload cleanup and refreshes the pending download flag`() {
        listOf("upload.jpg" to "image/jpeg", "voice.ogg" to "audio/ogg").stream().forEach { (name, mimeType) ->
            val source = Files.writeString(directory.resolve(name), "Synthetic $name")
            val retained = TelegramOutgoingFileStore(directory, Clock.systemUTC()).retain(source)
            val file = localFile(retained)
            val store = TelegramMediaStore(directory.resolve("telegram/files")) { file }
            file.local.isDownloadingCompleted = false
            store.register(file, mimeType, name)
            assertFalse(store.isDownloaded(1))
            Files.delete(source)
            file.local.isDownloadingCompleted = true
            store.register(file, mimeType, name)
            assertTrue(store.isDownloaded(1))
            assertEquals("Synthetic $name", Files.readString(store.get(1).path))
        }
    }

    @Test
    fun `recovers an old registered voice under the same session without exposing other generated files`() {
        val generated = Files.createDirectories(directory.resolve("speech/generated"))
        val source = Files.writeString(generated.resolve("voice.ogg"), "OggS synthetic")
        val file = localFile(source)
        file.remote = TdApi.RemoteFile().apply { uniqueId = "synthetic-voice" }
        var session = 1L
        val outgoing = TelegramOutgoingFileStore(directory, Clock.systemUTC())
        val store = TelegramMediaStore(directory.resolve("telegram/files"), emptyList(), outgoing, { session }) { file }
        assertFailsWith<InvalidInputException> { store.get(1) }
        store.register(file, "audio/ogg", "voice.ogg")
        val restored = store.get(1)
        assertTrue(restored.path.startsWith(directory.resolve("telegram/files/outgoing").toRealPath()))
        assertEquals("OggS synthetic", Files.readString(restored.path))
        assertEquals(restored.path, store.get(1).path)
        store.invalidate()
        file.local.path = Files.writeString(directory.resolve("private.ogg"), "OggS private").toString()
        assertFailsWith<MediaUnavailableException> { store.get(1) }
        file.local.path = source.toString()
        file.remote.uniqueId = "different-file"
        assertFailsWith<MediaUnavailableException> { store.get(1) }
        file.remote.uniqueId = "synthetic-voice"
        val raced = TelegramMediaStore(directory.resolve("telegram/files"), emptyList(), outgoing, { session }) {
            session++
            file
        }
        raced.register(file, "audio/ogg", "voice.ogg")
        assertFailsWith<MediaUnavailableException> { raced.get(1) }
    }

    @Test
    fun `does not reuse a recovered copy after file identity changes or the connection is replaced`() {
        val generated = Files.createDirectories(directory.resolve("speech/generated"))
        val source = Files.writeString(generated.resolve("voice.ogg"), "OggS first voice")
        val file = localFile(source)
        file.remote = TdApi.RemoteFile().apply { uniqueId = "first-remote" }
        var session = 1L
        val outgoing = TelegramOutgoingFileStore(directory, Clock.systemUTC())
        val store = TelegramMediaStore(directory.resolve("telegram/files"), emptyList(), outgoing, { session }) { file }
        store.register(file, "audio/ogg", "voice.ogg")
        val first = store.get(1).path
        Files.writeString(source, "OggS second voice")
        file.remote.uniqueId = "second-remote"
        store.register(file, "audio/ogg", "voice.ogg")
        val second = store.get(1).path
        assertEquals("OggS second voice", Files.readString(second))
        assertTrue(first != second)
        session++
        Files.writeString(source, "OggS reconnected voice")
        store.register(file, "audio/ogg", "voice.ogg")
        val reconnected = store.get(1).path
        assertEquals("OggS reconnected voice", Files.readString(reconnected))
        assertTrue(second != reconnected)
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
