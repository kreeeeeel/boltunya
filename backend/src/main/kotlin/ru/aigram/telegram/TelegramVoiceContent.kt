package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.springframework.stereotype.Component
import ru.aigram.domain.speech.OutgoingVoiceNote
import ru.aigram.exception.impl.InvalidMediaIdentifierException
import ru.aigram.exception.impl.InvalidSpeechAudioException
import ru.aigram.exception.impl.SpeechAudioLimitException
import java.nio.file.Files
import java.nio.file.LinkOption

private const val MAX_VOICE_SECONDS = 600
private const val MAX_VOICE_BYTES = 25L * 1024 * 1024
private const val MAX_WAVEFORM_BYTES = 63

@Component
class TelegramVoiceContent {
    fun request(chatId: Long, voice: OutgoingVoiceNote, replyToId: String, topicId: Int): TdApi.SendMessage {
        val path = voice.path.toAbsolutePath().normalize()
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || voice.duration !in 1..MAX_VOICE_SECONDS ||
            voice.waveform.size > MAX_WAVEFORM_BYTES || Files.size(path) == 0L
        ) {
            throw InvalidSpeechAudioException()
        }
        if (Files.size(path) > MAX_VOICE_BYTES) {
            throw SpeechAudioLimitException()
        }
        val header = Files.newInputStream(path).use { it.readNBytes(4) }
        if (!header.contentEquals("OggS".toByteArray(Charsets.US_ASCII))) {
            throw InvalidSpeechAudioException()
        }
        val reply = if (replyToId.isEmpty()) {
            null
        } else {
            val id = replyToId.toLongOrNull()
            if (id == null || id <= 0) {
                throw InvalidMediaIdentifierException()
            }
            TdApi.InputMessageReplyToMessage(id, null, 0, "")
        }
        if (chatId == 0L || topicId < 0) {
            throw InvalidMediaIdentifierException()
        }
        return TdApi.SendMessage().apply {
            this.chatId = chatId
            replyTo = reply
            if (topicId != 0) {
                this.topicId = TdApi.MessageTopicForum(topicId)
            }
            inputMessageContent = TdApi.InputMessageVoiceNote().apply {
                voiceNote = TdApi.InputVoiceNote(TdApi.InputFileLocal(path.toString()), voice.duration, voice.waveform)
                caption = TdApi.FormattedText("", emptyArray())
            }
        }
    }
}
