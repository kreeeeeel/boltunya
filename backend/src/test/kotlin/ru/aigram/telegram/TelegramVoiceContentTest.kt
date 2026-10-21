package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.speech.OutgoingVoiceNote
import ru.aigram.exception.impl.InvalidMediaIdentifierException
import ru.aigram.exception.impl.InvalidSpeechAudioException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class TelegramVoiceContentTest {
    @TempDir lateinit var directory: Path
    private val content = TelegramVoiceContent()

    @Test
    fun `uses a real voice note input with exact duration waveform reply and forum topic`() {
        val voice = voice()
        val request = content.request(-10042, voice, "9007199254740993", 19)
        val input = assertIs<TdApi.InputMessageVoiceNote>(request.inputMessageContent)
        val file = assertIs<TdApi.InputFileLocal>(input.voiceNote.voiceNote)
        assertEquals(voice.path.toAbsolutePath().toString(), file.path)
        assertEquals(2, input.voiceNote.duration)
        assertContentEquals(voice.waveform, input.voiceNote.waveform)
        assertEquals("", input.caption.text)
        assertEquals(-10042L, request.chatId)
        assertEquals(9007199254740993L, assertIs<TdApi.InputMessageReplyToMessage>(request.replyTo).messageId)
        assertEquals(19, assertIs<TdApi.MessageTopicForum>(request.topicId).forumTopicId)
        assertNull(request.options)
    }

    @Test
    fun `rejects invalid metadata and files before constructing a native sending request`() {
        val voice = voice()
        listOf(
            voice.copy(duration = 0),
            voice.copy(duration = 601),
            voice.copy(waveform = ByteArray(64)),
            voice.copy(path = directory.resolve("missing.ogg")),
        ).stream().forEach { invalid ->
            assertFailsWith<InvalidSpeechAudioException> { content.request(42, invalid, "", 0) }
        }
        Files.writeString(voice.path, "RIFF a WAV must not be sent as an OGG voice")
        assertFailsWith<InvalidSpeechAudioException> { content.request(42, voice, "", 0) }
    }

    @Test
    fun `validates reply and topic identifiers without narrowing Telegram ids`() {
        val voice = voice()
        assertFailsWith<InvalidMediaIdentifierException> { content.request(42, voice, "0", 0) }
        assertFailsWith<InvalidMediaIdentifierException> { content.request(42, voice, "bad", 0) }
        assertFailsWith<InvalidMediaIdentifierException> { content.request(42, voice, "", -1) }
        assertFailsWith<InvalidMediaIdentifierException> { content.request(0, voice, "", 0) }
        assertNull(content.request(42, voice, "", 0).replyTo)
    }

    private fun voice(): OutgoingVoiceNote {
        val path = directory.resolve("voice.ogg")
        Files.write(path, "OggS synthetic trusted Opus output".toByteArray(Charsets.US_ASCII))
        return OutgoingVoiceNote(path, 2, ByteArray(63) { 15 })
    }
}
