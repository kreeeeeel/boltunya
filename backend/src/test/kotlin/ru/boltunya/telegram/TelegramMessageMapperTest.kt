package ru.boltunya.telegram

import it.tdlight.jni.TdApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TelegramMessageMapperTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `preserves video note dimensions and packed voice waveform`() {
        val mapper = mapper()
        val videoMessage = TdApi.Message().apply {
            content = TdApi.MessageVideoNote().apply {
                videoNote = TdApi.VideoNote().apply {
                    video = file(5)
                    length = 240
                    duration = 12
                }
            }
        }
        val video = mapper.map(videoMessage)
        assertEquals("videoNote", video.kind)
        assertEquals(240, assertNotNull(video.media).width)
        assertEquals(12, video.media.duration)
        val voiceMessage = TdApi.Message().apply {
            content = TdApi.MessageVoiceNote().apply {
                voiceNote =
                    TdApi.VoiceNote().apply {
                        voice = file(6)
                        duration = 22
                        waveform = byteArrayOf(-1, 32, 0)
                    }
                caption = TdApi.FormattedText("", emptyArray())
            }
        }
        val voice = mapper.map(voiceMessage)
        assertEquals(listOf(255, 32, 0), assertNotNull(voice.media).waveform)
        assertEquals(22, voice.media.duration)
    }

    @Test
    fun `keeps photo caption separate from the largest image and chat preview`() {
        val mapper = mapper()
        val photo = TdApi.Photo().apply {
            sizes = arrayOf(
                TdApi.PhotoSize("s", file(1), 90, 90, intArrayOf()),
                TdApi.PhotoSize("x", file(2), 1280, 960, intArrayOf()),
            )
        }
        val message = TdApi.Message().apply {
            id = 100
            chatId = 42
            content = TdApi.MessagePhoto().apply {
                this.photo = photo
                caption = TdApi.FormattedText("Подпись", emptyArray())
            }
        }
        val mapped = mapper.map(message)
        assertEquals("photo", mapped.kind)
        assertEquals("Подпись", mapped.text)
        assertEquals(2, assertNotNull(mapped.media).fileId)
        assertEquals(1280, mapped.media.width)
        assertEquals("Фото · Подпись", mapper.preview(message))
    }

    @Test
    fun `preserves animated sticker format and does not replace it with emoji text`() {
        val sticker = TdApi.Sticker().apply {
            format = TdApi.StickerFormatTgs()
            this.sticker = file(3)
            width = 512
            height = 512
            emoji = "🙂"
        }
        val message = TdApi.Message().apply {
            content = TdApi.MessageSticker().apply { this.sticker = sticker }
        }
        val mapped = mapper().map(message)
        assertEquals("sticker", mapped.kind)
        assertEquals("", mapped.text)
        assertEquals("application/x-tgsticker", assertNotNull(mapped.media).mimeType)
    }

    private fun mapper(): TelegramMessageMapper = TelegramMessageMapper(TelegramMediaStore(directory) { file(it) })

    private fun file(id: Int): TdApi.File = TdApi.File().apply { this.id = id }
}
