package ru.boltunya.media

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.domain.OutgoingMediaKind
import ru.boltunya.exception.impl.InvalidUploadException
import ru.boltunya.exception.impl.MediaTooLargeException
import ru.boltunya.exception.impl.UploadNotFoundException
import ru.boltunya.memory.AdvancingClock
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UploadStoreTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `isolates trusted upload IDs by account and retains bytes until explicit removal`() {
        val store = UploadStore(directory, Clock.systemUTC())
        val bytes = byteArrayOf(1, 2, 3)
        val upload = store.create("42", "../photo.png", "image/png", ByteArrayInputStream(bytes), bytes.size.toLong())
        val media = store.media("42", upload.id, OutgoingMediaKind.PHOTO, "caption")
        assertEquals(".._photo.png", upload.fileName)
        assertEquals("caption", media.caption)
        assertEquals(OutgoingMediaKind.PHOTO, store.media("42", upload.id, OutgoingMediaKind.MEDIA, "").kind)
        assertTrue(media.path.startsWith(directory.resolve("uploads")))
        assertContentEquals(bytes, Files.readAllBytes(media.path))
        assertFailsWith<UploadNotFoundException> { store.media("43", upload.id, OutgoingMediaKind.PHOTO, "") }
        assertFailsWith<UploadNotFoundException> { store.media("42", "../outside", OutgoingMediaKind.PHOTO, "") }
        store.remove("43", upload.id)
        assertTrue(Files.exists(media.path))
        store.remove("42", upload.id)
        assertFalse(Files.exists(media.path))
        assertFailsWith<UploadNotFoundException> { store.media("42", upload.id, OutgoingMediaKind.PHOTO, "") }
    }

    @Test
    fun `sanitizes Windows filenames and rejects reserved device names`() {
        val store = UploadStore(directory, Clock.systemUTC())
        listOf("CON", "con.txt", "NUL.mp4", "com1.png", "LPT9.doc", "AUX .txt").stream().forEach { name ->
            assertFailsWith<InvalidUploadException>(name) {
                store.create("42", name, "image/png", ByteArrayInputStream(byteArrayOf(1)), 1)
            }
        }
        val upload = store.create("42", "a\u202Eb?c.png", "image/png", ByteArrayInputStream(byteArrayOf(1)), 1)
        assertEquals("a_b_c.png", upload.fileName)
    }

    @Test
    fun `cleans incomplete uploads and prevents MIME kind confusion`() {
        val store = UploadStore(directory, Clock.systemUTC())
        assertFailsWith<InvalidUploadException> {
            store.create("42", "image.png", "image/png", ByteArrayInputStream(byteArrayOf(1)), 2)
        }
        Files.list(directory.resolve("uploads")).use { assertEquals(0L, it.count()) }
        assertFailsWith<MediaTooLargeException> {
            store.create(
                "42",
                "large",
                "image/png",
                ByteArrayInputStream(byteArrayOf(1)),
                101L * 1024 * 1024,
            )
        }
        val upload = store.create(
            "42",
            "document.bin",
            "application/x-executable",
            ByteArrayInputStream(byteArrayOf(1)),
            1,
        )
        assertEquals("application/octet-stream", upload.mimeType)
        assertFailsWith<InvalidUploadException> { store.media("42", upload.id, OutgoingMediaKind.PHOTO, "") }
        assertFailsWith<InvalidUploadException> { store.media("42", upload.id, OutgoingMediaKind.VIDEO, "") }
        assertFailsWith<InvalidUploadException> { store.media("42", upload.id, OutgoingMediaKind.MEDIA, "") }
        assertEquals(OutgoingMediaKind.DOCUMENT, store.media("42", upload.id, OutgoingMediaKind.DOCUMENT, "").kind)
    }

    @Test
    fun `expires abandoned staged files instead of exposing them indefinitely`() {
        val clock = AdvancingClock(Instant.parse("2026-10-21T10:00:00Z"))
        val store = UploadStore(directory, clock)
        val upload = store.create("42", "photo.jpg", "image/jpeg", ByteArrayInputStream(byteArrayOf(1)), 1)
        val path = store.media("42", upload.id, OutgoingMediaKind.PHOTO, "").path
        clock.advance(Duration.ofHours(2))
        assertFailsWith<UploadNotFoundException> { store.media("42", upload.id, OutgoingMediaKind.PHOTO, "") }
        assertFalse(Files.exists(path))
    }
}
