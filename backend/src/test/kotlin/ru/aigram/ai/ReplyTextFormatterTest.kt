package ru.aigram.ai

import org.junit.jupiter.api.Test
import ru.aigram.domain.ai.FormattedReply
import ru.aigram.domain.chat.MessageEntity
import ru.aigram.telegram.TelegramOutgoingEntities
import java.util.stream.IntStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReplyTextFormatterTest {
    private val formatter = ReplyTextFormatter()
    private val native = TelegramOutgoingEntities()

    @Test
    fun `disabled formatting preserves every literal marker whitespace and code character`() {
        val source = "\n **bold** *italic* ~~strike~~ ||secret|| [link](https://example.com)\n" +
            "```kotlin\n  x = `literal`\n```\n"
        assertEquals(FormattedReply(source), formatter.format(source, false))
        assertEquals(FormattedReply("  обычный текст\n\n"), formatter.format("  обычный текст\n\n", true))
    }

    @Test
    fun `nested Markdown and extension delimiters produce UTF16 Telegram entities`() {
        val result = formatter.format("😀 **Привет, *мир*** и ||секрет|| ~~нет~~.", true)
        val text = "😀 Привет, мир и секрет нет."
        assertEquals(text, result.text)
        assertEquals(
            listOf(
                MessageEntity(3, "Привет, мир".length, "bold"),
                MessageEntity(text.indexOf("мир"), 3, "italic"),
                MessageEntity(text.indexOf("секрет"), 6, "spoiler"),
                MessageEntity(text.indexOf("нет"), 3, "strike"),
            ),
            result.entities,
        )
        assertEquals(text, native.format(result.text, result.entities).text)
    }

    @Test
    fun `links retain their exact destination and nested labels without HTML parsing`() {
        val result = formatter.format("[**док**](https://example.com/path?q=1) и <b>буквально</b>", true)
        assertEquals("док и <b>буквально</b>", result.text)
        assertTrue(result.entities.contains(MessageEntity(0, 3, "texturl", "https://example.com/path?q=1")))
        assertTrue(result.entities.contains(MessageEntity(0, 3, "bold")))
        native.format(result.text, result.entities)
    }

    @Test
    fun `code stays literal with language metadata and surrounding styles exclude code ranges`() {
        val code = "val x = \"**literal**\"\n"
        val result = formatter.format("**до `a * b` после**\n\n```kotlin\n$code```", true)
        assertEquals("до a * b после\n\n$code", result.text)
        assertTrue(result.entities.contains(MessageEntity(3, 5, "code")))
        assertTrue(result.entities.contains(MessageEntity(0, 3, "bold")))
        assertTrue(result.entities.contains(MessageEntity(8, 6, "bold")))
        assertTrue(
            result.entities.contains(
                MessageEntity(result.text.indexOf(code), code.length, "precode", language = "kotlin"),
            ),
        )
        native.format(result.text, result.entities)
    }

    @Test
    fun `quotes keep line breaks and allow a native code entity inside the quote`() {
        val result = formatter.format("> **Важно**\n> `код`\n\nДальше", true)
        assertEquals("Важно\nкод\n\nДальше", result.text)
        assertTrue(result.entities.contains(MessageEntity(0, "Важно\nкод".length, "quote")))
        assertTrue(result.entities.contains(MessageEntity(0, 5, "bold")))
        assertTrue(result.entities.contains(MessageEntity(6, 3, "code")))
        native.format(result.text, result.entities)
    }

    @Test
    fun `escaped delimiters and unmatched syntax stay visible`() {
        val result = formatter.format("\\*буквально\\* **ок**", true)
        assertEquals("*буквально* ок", result.text)
        assertEquals(listOf(MessageEntity(result.text.lastIndexOf("ок"), 2, "bold")), result.entities)
        assertEquals(FormattedReply("**не закрыто"), formatter.format("**не закрыто", true))
        native.format(result.text, result.entities)
    }

    @Test
    fun `unsupported images and unsafe links retain the entire literal response including reference definitions`() {
        listOf(
            "![Карта][image] **Важно**\n\n[image]: https://example.com/map.png",
            "[ссылка](javascript:alert%281%29) \\*буквально\\* **ок**",
            "[ссылка][target] **ок**\n\n[target]: javascript:alert%281%29",
        ).stream().forEach { source ->
            assertEquals(FormattedReply(source), formatter.format(source, true))
        }
    }

    @Test
    fun `excessive formatting falls back to the complete literal response instead of losing content`() {
        val source = IntStream.range(0, 101).mapToObj { "**x**" }.toList().joinToString(" ")
        assertEquals(FormattedReply(source), formatter.format(source, true))
    }

    @Test
    fun `leading and trailing empty lines remain around formatted content`() {
        val result = formatter.format("\n\n**текст**\n\n", true)
        assertEquals("\n\nтекст\n\n", result.text)
        assertEquals(listOf(MessageEntity(2, 5, "bold")), result.entities)
    }
}
