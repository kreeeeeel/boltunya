package ru.aigram.speech.reply

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.speech.OutgoingVoiceNote
import ru.aigram.exception.impl.ReplyCancelledException
import ru.aigram.exception.impl.SpeechDraftUnavailableException
import ru.aigram.exception.impl.TelegramSendFailedException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.stream.IntStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceDraftPruningTest {
    @TempDir
    lateinit var directory: Path
    private val clock = VoiceTestClock()

    @Test
    fun `restart cleanup removes only expired canonical owned voices`() {
        val generated = Files.createDirectories(directory.resolve("speech/generated"))
        val expired = expired(generated.resolve("${UUID.randomUUID()}.ogg"))
        val fresh = Files.write(generated.resolve("${UUID.randomUUID()}.ogg"), byteArrayOf(1))
        Files.setLastModifiedTime(fresh, FileTime.from(clock.instant()))
        val unknown = expired(generated.resolve("manual.ogg"))
        val differentExtension = expired(generated.resolve("${UUID.randomUUID()}.wav"))
        val folder = Files.createDirectory(generated.resolve("${UUID.randomUUID()}.ogg"))

        VoiceDraftStore(directory, VoiceTestGateway(), clock).output()

        assertFalse(Files.exists(expired))
        assertTrue(Files.exists(fresh))
        assertTrue(Files.exists(unknown))
        assertTrue(Files.exists(differentExtension))
        assertTrue(Files.isDirectory(folder))
    }

    @Test
    fun `ambiguous native upload remains protected after expiry and restart`() {
        val gateway = VoiceTestGateway()
        val store = VoiceDraftStore(directory, gateway, clock)
        val file = store.output()
        Files.write(file, byteArrayOf(1))
        val voice = store.register("100", "42", OutgoingVoiceNote(file, 1, byteArrayOf(0)))
        gateway.delegate.nextSendError = TelegramSendFailedException()
        assertFailsWith<TelegramSendFailedException> { store.send("42", voice.id, "", 0, false) }
        val marker = file.resolveSibling("${voice.id}.sending")
        assertTrue(Files.isRegularFile(marker))
        clock.current = clock.current.plus(Duration.ofHours(2))
        Files.setLastModifiedTime(file, FileTime.from(clock.instant().minus(Duration.ofHours(2))))

        store.output()
        VoiceDraftStore(directory, gateway, clock).output()

        assertTrue(Files.exists(file))
        assertTrue(Files.exists(marker))
    }

    @Test
    fun `pruning cannot remove a file while native delivery is in flight`() {
        val gateway = BlockingVoiceTestGateway()
        val store = VoiceDraftStore(directory, gateway, clock)
        val file = store.output()
        Files.write(file, byteArrayOf(1))
        val voice = store.register("100", "42", OutgoingVoiceNote(file, 1, byteArrayOf(0)))
        Executors.newSingleThreadExecutor().use { executor ->
            val sending = executor.submit<String> { store.send("42", voice.id, "", 0, false) }
            try {
                assertTrue(gateway.started.await(3, TimeUnit.SECONDS))
                clock.current = clock.current.plus(Duration.ofHours(2))
                assertEquals(file, store.audio(voice.id))
                assertTrue(Files.exists(file))
            } finally {
                gateway.finish.countDown()
            }
            assertEquals("voice-1", sending.get(3, TimeUnit.SECONDS))
        }
        assertFalse(Files.exists(file.resolveSibling("${voice.id}.sending")))
    }

    @Test
    fun `final guard rejection creates no marker and confirmed success can expire after restart`() {
        val store = VoiceDraftStore(directory, VoiceTestGateway(), clock)
        val file = store.output()
        Files.write(file, byteArrayOf(1))
        val voice = store.register("100", "42", OutgoingVoiceNote(file, 1, byteArrayOf(0)))
        val marker = file.resolveSibling("${voice.id}.sending")
        assertFailsWith<ReplyCancelledException> {
            store.send("42", voice.id, "", 0, false) { throw ReplyCancelledException() }
        }
        assertFalse(Files.exists(marker))
        assertEquals("voice-1", store.send("42", voice.id, "", 0, false))
        assertFalse(Files.exists(marker))
        clock.current = clock.current.plus(Duration.ofHours(2))
        Files.setLastModifiedTime(file, FileTime.from(clock.instant().minus(Duration.ofHours(2))))

        VoiceDraftStore(directory, VoiceTestGateway(), clock).output()

        assertFalse(Files.exists(file))
    }

    @Test
    fun `orphan scan has a fixed work bound`() {
        val generated = Files.createDirectories(directory.resolve("speech/generated"))
        IntStream.range(0, 1050).forEach { expired(generated.resolve("${UUID.randomUUID()}.ogg")) }

        VoiceDraftStore(directory, VoiceTestGateway(), clock).output()

        Files.list(generated).use { files -> assertEquals(26L, files.count()) }
    }

    @Test
    fun `cleanup never follows a symbolic link to unrelated audio`() {
        val generated = Files.createDirectories(directory.resolve("speech/generated"))
        val target = expired(directory.resolve("unrelated.ogg"))
        val link = generated.resolve("${UUID.randomUUID()}.ogg")
        try {
            Files.createSymbolicLink(link, target)
        } catch (exception: FileSystemException) {
            assumeTrue(false, "Symbolic links require an available Windows privilege")
        } catch (exception: UnsupportedOperationException) {
            assumeTrue(false, "Symbolic links are not supported by this filesystem")
        }

        VoiceDraftStore(directory, VoiceTestGateway(), clock).output()

        assertTrue(Files.exists(target))
        assertTrue(Files.exists(link, LinkOption.NOFOLLOW_LINKS))
    }

    @Test
    fun `tracked expiry cannot follow a replaced generated directory link`() {
        val store = VoiceDraftStore(directory, VoiceTestGateway(), clock)
        val original = store.output()
        Files.write(original, byteArrayOf(1))
        val voice = store.register("100", "42", OutgoingVoiceNote(original, 1, byteArrayOf(0)))
        val preserved = directory.resolve("preserved")
        Files.move(original.parent, preserved)
        val unrelated = Files.createDirectories(directory.resolve("unrelated"))
        val outside = expired(unrelated.resolve(original.fileName))
        try {
            Files.createSymbolicLink(original.parent, unrelated)
        } catch (exception: FileSystemException) {
            assumeTrue(false, "Symbolic links require an available Windows privilege")
        } catch (exception: UnsupportedOperationException) {
            assumeTrue(false, "Symbolic links are not supported by this filesystem")
        }
        clock.current = clock.current.plus(Duration.ofHours(2))

        assertFailsWith<SpeechDraftUnavailableException> { store.audio(voice.id) }

        assertTrue(Files.exists(outside))
        assertTrue(Files.exists(preserved.resolve(original.fileName)))
    }

    private fun expired(path: Path): Path {
        Files.write(path, byteArrayOf(1))
        Files.setLastModifiedTime(path, FileTime.from(clock.instant().minus(Duration.ofHours(2))))
        return path
    }
}
