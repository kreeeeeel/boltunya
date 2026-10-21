package ru.aigram.telegram

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.exception.impl.MediaUnavailableException
import ru.aigram.exception.impl.UploadLimitReachedException
import ru.aigram.memory.AdvancingClock
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.stream.IntStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelegramOutgoingFileStoreTest {
    @TempDir lateinit var directory: Path
    private val clock = AdvancingClock(Instant.parse("2026-10-22T00:00:00Z"))

    @Test
    fun `retains an independent immutable input when the staged attachment or voice draft is removed`() {
        val store = TelegramOutgoingFileStore(directory, clock)
        listOf("uploads/photo.jpg", "speech/generated/voice.ogg").stream().forEach { name ->
            val source = directory.resolve(name)
            Files.createDirectories(source.parent)
            val bytes = "OggS synthetic media $name".toByteArray()
            Files.write(source, bytes)
            val retained = store.retain(source)
            Files.writeString(source, "changed source")
            Files.delete(source)
            assertContentEquals(bytes, Files.readAllBytes(retained))
            assertTrue(retained.startsWith(directory.resolve("telegram/files/outgoing")))
            assertEquals(source.fileName, retained.fileName)
            assertTrue(Files.exists(retained.parent.resolve(".sending")))
        }
    }

    @Test
    fun `cache clear and retention preserve active and uncertain uploads across process restart`() {
        val store = TelegramOutgoingFileStore(directory, clock)
        val source = Files.writeString(directory.resolve("voice.ogg"), "OggS synthetic")
        val active = store.retain(source)
        val complete = store.retain(source)
        store.completed(complete)
        clock.advance(Duration.ofDays(2))
        val restarted = TelegramOutgoingFileStore(directory, clock)
        restarted.retain(source)
        assertTrue(Files.exists(active))
        assertFalse(Files.exists(complete))
        restarted.clearCompleted()
        assertTrue(Files.exists(active))
        restarted.completed(active)
        restarted.clearCompleted()
        assertFalse(Files.exists(active))
    }

    @Test
    fun `active and uncertain copies count towards the byte limit without deleting them`() {
        val root = directory.resolve("telegram/files/outgoing")
        val pinned = IntStream.range(0, 5).mapToObj {
            val entry = Files.createDirectories(root.resolve(UUID.randomUUID().toString()))
            Files.createFile(entry.resolve(".sending"))
            val file = entry.resolve("voice.ogg")
            FileChannel.open(
                file,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
                StandardOpenOption.SPARSE,
            ).use { channel ->
                channel.position(100L * 1024 * 1024 - 1)
                channel.write(ByteBuffer.wrap(byteArrayOf(1)))
            }
            file
        }.toList()
        val source = Files.writeString(directory.resolve("voice.ogg"), "OggS synthetic")
        val store = TelegramOutgoingFileStore(directory, clock)
        assertFailsWith<UploadLimitReachedException> { store.retain(source) }
        store.clearCompleted()
        assertTrue(pinned.stream().allMatch(Files::exists))
    }

    @Test
    fun `a bounded number of uncertain upload entries survives cleanup without unbounded growth`() {
        val root = directory.resolve("telegram/files/outgoing")
        IntStream.range(0, 1024).forEach {
            val entry = Files.createDirectories(root.resolve(UUID.randomUUID().toString()))
            Files.createFile(entry.resolve(".sending"))
        }
        val source = Files.writeString(directory.resolve("voice.ogg"), "OggS synthetic")
        assertFailsWith<UploadLimitReachedException> { TelegramOutgoingFileStore(directory, clock).retain(source) }
    }

    @Test
    fun `recovery accepts only generated Ogg voices and never adjacent files`() {
        val generated = Files.createDirectories(directory.resolve("speech/generated"))
        val source = Files.writeString(generated.resolve("voice.ogg"), "OggS synthetic")
        val store = TelegramOutgoingFileStore(directory, clock)
        val copy = store.recoverVoice(source)
        assertEquals("OggS synthetic", Files.readString(copy))
        assertFalse(Files.exists(copy.parent.resolve(".sending")))
        val foreign = Files.writeString(directory.resolve("private.ogg"), "OggS private")
        assertFailsWith<MediaUnavailableException> { store.recoverVoice(foreign) }
        Files.writeString(source, "not an Ogg voice")
        assertFailsWith<MediaUnavailableException> { store.recoverVoice(source) }
    }

    @Test
    fun `cancelled or exceptional native futures preserve pins while late successful receipts release them`() {
        val store = TelegramOutgoingFileStore(directory, clock)
        val source = Files.writeString(directory.resolve("voice.ogg"), "OggS synthetic")
        val active = store.retain(source)
        val cancelled = store.retain(source)
        val uncertain = store.retain(source)
        val success = CompletableFuture<String>()
        val cancellation = CompletableFuture<String>()
        val failure = CompletableFuture<String>()
        store.track(listOf(active), success)
        store.track(listOf(cancelled), cancellation)
        store.track(listOf(uncertain), failure)
        cancellation.cancel(false)
        failure.completeExceptionally(IllegalStateException("Partial native album acceptance"))
        store.clearCompleted()
        assertTrue(Files.exists(active))
        assertTrue(Files.exists(cancelled))
        assertTrue(Files.exists(uncertain))
        success.complete("9007199254740993")
        store.clearCompleted()
        assertFalse(Files.exists(active))
        assertTrue(Files.exists(cancelled))
        assertTrue(Files.exists(uncertain))
    }

    @Test
    fun `legacy voice recovery rejects a symbolic link escaping the generated directory`() {
        val generated = Files.createDirectories(directory.resolve("speech/generated"))
        val outside = Files.writeString(directory.resolve("private.ogg"), "OggS private")
        val link = generated.resolve("voice.ogg")
        try {
            Files.createSymbolicLink(link, outside)
        } catch (exception: FileSystemException) {
            assumeTrue(false, "Symbolic links need privileges on this filesystem")
        } catch (exception: UnsupportedOperationException) {
            assumeTrue(false, "Symbolic links are unsupported on this filesystem")
        }
        val store = TelegramOutgoingFileStore(directory, clock)
        assertFailsWith<MediaUnavailableException> { store.recoverVoice(link) }
        assertEquals("OggS private", Files.readString(outside))
    }
}
