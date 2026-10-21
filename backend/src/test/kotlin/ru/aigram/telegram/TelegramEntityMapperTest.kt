package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TelegramEntityMapperTest {
    @Test
    fun `preserves UTF16 offsets and explicit link targets`() {
        val text = TdApi.FormattedText(
            "🙂 Привет сайт",
            arrayOf(
                TdApi.TextEntity(3, 6, TdApi.TextEntityTypeBold()),
                TdApi.TextEntity(10, 4, TdApi.TextEntityTypeTextUrl("https://example.com")),
            ),
        )
        val entities = TelegramEntityMapper().map(text)
        assertEquals(3, entities[0].offset)
        assertEquals("bold", entities[0].type)
        assertEquals("Привет", text.text.substring(entities[0].offset, entities[0].offset + entities[0].length))
        assertEquals("https://example.com", entities[1].url)
    }
}
