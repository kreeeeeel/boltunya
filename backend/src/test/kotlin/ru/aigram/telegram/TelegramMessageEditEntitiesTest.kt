package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.junit.jupiter.api.Test
import ru.aigram.domain.chat.MessageEditEntity
import ru.aigram.domain.chat.MessageEditEntityKind
import ru.aigram.exception.impl.InvalidMessageEditException
import java.util.stream.IntStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class TelegramMessageEditEntitiesTest {
    private val entities = TelegramMessageEditEntities()

    @Test
    fun `original entities keep every native metadata field while offsets shift without parsing text`() {
        val text = "😀 Bob url code 00:07"
        val original = TdApi.FormattedText(
            text,
            arrayOf(
                TdApi.TextEntity(0, 2, TdApi.TextEntityTypeCustomEmoji(9007199254740993L)),
                TdApi.TextEntity(3, 3, TdApi.TextEntityTypeMentionName(9007199254740994L)),
                TdApi.TextEntity(7, 3, TdApi.TextEntityTypeTextUrl("https://example.org/original")),
                TdApi.TextEntity(11, 4, TdApi.TextEntityTypePreCode("kotlin")),
                TdApi.TextEntity(16, 5, TdApi.TextEntityTypeMediaTimestamp(7)),
            ),
        )
        val options = entities.describe(original)
        assertEquals(
            listOf("customemoji", "mentionname", "texturl", "precode", "mediatimestamp"),
            options.stream().map { it.typeHint }.toList(),
        )
        assertEquals("https://example.org/original", options[2].url)
        assertEquals("kotlin", options[3].language)
        assertEquals(listOf(0, 1, 2, 3, 4), options.stream().map { it.sourceIndex }.toList())
        val changed = options.stream().map {
            it.copy(offset = it.offset + 2, url = "https://ignored.invalid", language = "ignored", typeHint = "ignored")
        }.toList()

        val result = entities.format("  $text \n", changed, original)

        assertEquals("  $text \n", result.text)
        IntStream.range(0, original.entities.size).forEach { index ->
            assertSame(original.entities[index].type, result.entities[index].type)
            assertEquals(original.entities[index].offset + 2, result.entities[index].offset)
            assertEquals(original.entities[index].length, result.entities[index].length)
        }
    }

    @Test
    fun `nonoverlapping original fragments are supported and forged references or overlaps are rejected`() {
        val original = TdApi.FormattedText("abcdef", arrayOf(TdApi.TextEntity(0, 6, TdApi.TextEntityTypeBold())))
        val first = MessageEditEntity(0, 2, MessageEditEntityKind.EXISTING, 0)
        val second = first.copy(offset = 4)
        val result = entities.format("abcdef", listOf(first, second), original)

        assertEquals(2, result.entities.size)
        assertSame(original.entities[0].type, result.entities[1].type)
        listOf(
            listOf(first, second.copy(offset = 1)),
            listOf(first.copy(sourceIndex = 1)),
            listOf(first.copy(sourceIndex = -1)),
            listOf(first.copy(sourceIndex = null)),
            listOf(first.copy(kind = MessageEditEntityKind.BOLD)),
        ).stream().forEach { invalid ->
            assertFailsWith<InvalidMessageEditException> { entities.format("abcdef", invalid, original) }
        }
    }

    @Test
    fun `entity offsets use utf16 and never split surrogate pairs or overflow the text`() {
        val original = TdApi.FormattedText("", emptyArray())
        val wholeEmoji = MessageEditEntity(1, 2, MessageEditEntityKind.BOLD)
        assertEquals(2, entities.format("A😀B", listOf(wholeEmoji), original).entities.single().length)
        listOf(
            wholeEmoji.copy(offset = 2, length = 1),
            wholeEmoji.copy(length = 1),
            wholeEmoji.copy(offset = -1),
            wholeEmoji.copy(offset = Int.MAX_VALUE),
            wholeEmoji.copy(length = Int.MAX_VALUE),
            wholeEmoji.copy(length = 0),
        ).stream().forEach { invalid ->
            assertFailsWith<InvalidMessageEditException> { entities.format("A😀B", listOf(invalid), original) }
        }
        listOf("\uD83D", "\uDE00", "a\uD83Db").stream().forEach { text ->
            assertFailsWith<InvalidMessageEditException> { entities.format(text, emptyList(), original) }
        }
    }

    @Test
    fun `new formatting entities use typed constructors without a markdown round trip`() {
        val kinds = MessageEditEntityKind.entries.stream().filter { it != MessageEditEntityKind.EXISTING }.toList()
        val marks = kinds.stream().map {
            MessageEditEntity(0, 3, it, url = "https://example.org", language = "kotlin")
        }.toList()
        val result = entities.format("<x>", marks, TdApi.FormattedText("", emptyArray()))

        assertEquals("<x>", result.text)
        assertEquals(kinds.size, result.entities.size)
        assertEquals(
            kinds.size.toLong(),
            result.entities.asList().stream().map { it.type.constructor }.distinct().count(),
        )
        assertFailsWith<InvalidMessageEditException> {
            entities.format("url", listOf(MessageEditEntity(0, 3, MessageEditEntityKind.TEXTURL)), result)
        }
    }
}
