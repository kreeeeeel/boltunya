package ru.aigram.speech.reply

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.speech.GeneratedVoice
import ru.aigram.domain.speech.OutgoingVoiceNote
import ru.aigram.exception.impl.ReplyCancelledException
import ru.aigram.exception.impl.SpeechDraftConsumedException
import ru.aigram.exception.impl.SpeechDraftUnavailableException
import ru.aigram.exception.impl.TelegramSendFailedException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceDraftStoreTest {
    @TempDir
    lateinit var directory: Path
    private val gateway = VoiceTestGateway()
    private val clock = VoiceTestClock()

    @Test
    fun `preview and delivery stay bound to owner and chat`() {
        val store = VoiceDraftStore(directory, gateway, clock)
        val voice = draft(store)
        assertTrue(Files.exists(store.audio(voice.id)))
        assertEquals(listOf(255, 31), voice.waveform)
        assertEquals("audio/ogg", voice.mimeType)
        assertEquals("/api/speech/audio/${voice.id}", voice.url)
        assertFailsWith<SpeechDraftUnavailableException> { store.send("43", voice.id, "", 0, false) }
        gateway.owner = "200"
        assertFailsWith<SpeechDraftUnavailableException> { store.audio(voice.id) }
        assertFailsWith<SpeechDraftUnavailableException> { store.send("42", voice.id, "", 0, false) }
        assertTrue(gateway.delegate.sentVoices.isEmpty())
    }

    @Test
    fun `successful native delivery is idempotent and never also sends text`() {
        val store = VoiceDraftStore(directory, gateway, clock)
        val voice = draft(store)
        var dispatches = 0
        assertEquals("voice-1", store.send("42", voice.id, "17", 12, true) { dispatches++ })
        assertEquals("voice-1", store.send("42", voice.id, "17", 12, true) { dispatches++ })
        assertEquals(1, dispatches)
        assertEquals(1, gateway.delegate.sentVoices.size)
        assertEquals(listOf(true), gateway.delegate.voiceAutomated)
        assertEquals(listOf("17"), gateway.delegate.voiceReplyIds)
        assertEquals(listOf(12), gateway.delegate.voiceTopicIds)
        assertEquals("100", gateway.delegate.sentVoices.single().ownerId)
        assertTrue(gateway.delegate.sent.isEmpty())
    }

    @Test
    fun `guard cancellation releases the claim before any native dispatch`() {
        val store = VoiceDraftStore(directory, gateway, clock)
        val voice = draft(store)
        assertFailsWith<ReplyCancelledException> {
            store.send("42", voice.id, "", 0, false) { throw ReplyCancelledException() }
        }
        assertTrue(gateway.delegate.sentVoices.isEmpty())
        store.send("42", voice.id, "", 0, false)
        assertEquals(1, gateway.delegate.sentVoices.size)
    }

    @Test
    fun `account change inside the final guard cannot dispatch an old account draft`() {
        val store = VoiceDraftStore(directory, gateway, clock)
        val voice = draft(store)
        assertFailsWith<SpeechDraftUnavailableException> {
            store.send("42", voice.id, "", 0, false) { gateway.owner = "200" }
        }
        assertTrue(gateway.delegate.sentVoices.isEmpty())
        gateway.owner = "100"
        store.send("42", voice.id, "", 0, false)
        assertEquals(1, gateway.delegate.sentVoices.size)
    }

    @Test
    fun `ambiguous native failure retains the upload and blocks duplicate retry`() {
        val store = VoiceDraftStore(directory, gateway, clock)
        val voice = draft(store)
        val path = store.audio(voice.id)
        gateway.delegate.nextSendError = TelegramSendFailedException()
        assertFailsWith<TelegramSendFailedException> { store.send("42", voice.id, "", 0, true) }
        assertTrue(Files.exists(path))
        assertFailsWith<SpeechDraftConsumedException> { store.send("42", voice.id, "", 0, true) }
        assertTrue(gateway.delegate.sentVoices.isEmpty())
    }

    @Test
    fun `expiry clears only owned output and rejects stale previews`() {
        val store = VoiceDraftStore(directory, gateway, clock)
        val voice = draft(store)
        val output = store.audio(voice.id)
        val unrelated = directory.resolve("manual-note.txt")
        Files.writeString(unrelated, "Preserve")
        clock.current = clock.current.plus(Duration.ofMinutes(61))
        assertFailsWith<SpeechDraftUnavailableException> { store.audio(voice.id) }
        assertFalse(Files.exists(output))
        assertTrue(Files.exists(unrelated))
    }

    @Test
    fun `an unrelated path is rejected without removing its contents`() {
        val store = VoiceDraftStore(directory, gateway, clock)
        val unrelated = directory.resolve("unrelated.ogg")
        Files.writeString(unrelated, "Preserve")
        assertFailsWith<SpeechDraftUnavailableException> {
            store.register("100", "42", OutgoingVoiceNote(unrelated, 1, byteArrayOf(0)))
        }
        assertEquals("Preserve", Files.readString(unrelated))
    }

    private fun draft(store: VoiceDraftStore): GeneratedVoice {
        val file = store.output()
        Files.write(file, byteArrayOf(79, 103, 103, 83))
        return store.register("100", "42", OutgoingVoiceNote(file, 5, byteArrayOf(-1, 31)))
    }
}
