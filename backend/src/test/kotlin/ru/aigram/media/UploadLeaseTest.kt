package ru.aigram.media

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.media.OutgoingMediaKind
import ru.aigram.exception.impl.InvalidUploadException
import ru.aigram.exception.impl.UploadNotFoundException
import ru.aigram.memory.AdvancingClock
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UploadLeaseTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `pins the entire batch atomically and defers discard until delivery finishes`() {
        val store = UploadStore(directory, Clock.systemUTC())
        val first = store.create("42", "one.png", "image/png", ByteArrayInputStream(byteArrayOf(1)), 1)
        val second = store.create("42", "two.png", "image/png", ByteArrayInputStream(byteArrayOf(2)), 1)
        assertFailsWith<InvalidUploadException> {
            store.begin("42", listOf(first.id, first.id), OutgoingMediaKind.MEDIA, "")
        }
        assertFailsWith<UploadNotFoundException> {
            store.begin("42", listOf(first.id, "missing"), OutgoingMediaKind.MEDIA, "")
        }
        val files = store.begin("42", listOf(first.id, second.id), OutgoingMediaKind.MEDIA, "Caption")
        assertEquals(listOf("Caption", ""), files.map { it.caption })
        assertFailsWith<InvalidUploadException> {
            store.begin("42", listOf(first.id), OutgoingMediaKind.MEDIA, "")
        }
        store.remove("42", first.id)
        files.stream().forEach { assertTrue(Files.exists(it.path)) }
        store.release("43", listOf(first.id, second.id), true)
        files.stream().forEach { assertTrue(Files.exists(it.path)) }
        store.release("42", listOf(first.id, second.id), true)
        files.stream().forEach { assertFalse(Files.exists(it.path)) }
    }

    @Test
    fun `retains failed deliveries for retry unless the client requested discard`() {
        val store = UploadStore(directory, Clock.systemUTC())
        val first = store.create("42", "one.png", "image/png", ByteArrayInputStream(byteArrayOf(1)), 1)
        val second = store.create("42", "two.png", "image/png", ByteArrayInputStream(byteArrayOf(2)), 1)
        val files = store.begin("42", listOf(first.id, second.id), OutgoingMediaKind.PHOTO, "")
        store.remove("42", first.id)
        store.release("42", listOf(first.id, second.id), false)
        assertFalse(Files.exists(files.first().path))
        assertTrue(Files.exists(files.last().path))
        assertFailsWith<UploadNotFoundException> {
            store.begin("42", listOf(first.id), OutgoingMediaKind.PHOTO, "")
        }
        assertEquals(files.last(), store.begin("42", listOf(second.id), OutgoingMediaKind.PHOTO, "").single())
        store.release("42", listOf(second.id), true)
        assertFalse(Files.exists(files.last().path))
    }

    @Test
    fun `expiry cleanup never removes pinned files even when their filesystem timestamp is old`() {
        val clock = AdvancingClock(Instant.parse("2026-10-21T10:00:00Z"))
        val store = UploadStore(directory, clock)
        val upload = store.create("42", "one.png", "image/png", ByteArrayInputStream(byteArrayOf(1)), 1)
        val file = store.begin("42", listOf(upload.id), OutgoingMediaKind.PHOTO, "").single()
        Files.setLastModifiedTime(file.path, FileTime.from(clock.instant()))
        clock.advance(Duration.ofHours(2))
        store.create("42", "two.png", "image/png", ByteArrayInputStream(byteArrayOf(2)), 1)
        assertTrue(Files.exists(file.path))
        store.release("42", listOf(upload.id), true)
        assertFalse(Files.exists(file.path))
    }

    @Test
    fun `an invalid later media item does not leave an earlier item pinned`() {
        val store = UploadStore(directory, Clock.systemUTC())
        val photo = store.create("42", "one.png", "image/png", ByteArrayInputStream(byteArrayOf(1)), 1)
        val document = store.create(
            "42",
            "two.bin",
            "application/octet-stream",
            ByteArrayInputStream(byteArrayOf(2)),
            1,
        )
        assertFailsWith<InvalidUploadException> {
            store.begin("42", listOf(photo.id, document.id), OutgoingMediaKind.PHOTO, "")
        }
        val file = store.begin("42", listOf(photo.id), OutgoingMediaKind.PHOTO, "").single()
        store.remove("42", photo.id)
        store.release("42", listOf(photo.id), false)
        assertFalse(Files.exists(file.path))
    }
}
