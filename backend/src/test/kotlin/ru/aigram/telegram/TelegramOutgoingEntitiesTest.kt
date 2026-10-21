package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.junit.jupiter.api.Test
import ru.aigram.domain.chat.MessageEntity
import ru.aigram.exception.impl.InvalidMessageEntitiesException
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TelegramOutgoingEntitiesTest {
    private val formatter = TelegramOutgoingEntities()

    @Test
    fun `constructs exact UTF16 entities and keeps literal text without parsing Markdown`() {
        val text = "  😀 bold **literal**  "
        val result = formatter.format(text, listOf(MessageEntity(5, 4, "bold")))
        assertEquals(text, result.text)
        assertEquals(5, result.entities.single().offset)
        assertEquals(4, result.entities.single().length)
        assertIs<TdApi.TextEntityTypeBold>(result.entities.single().type)
        assertTrue(formatter.format(text, emptyList()).entities.isEmpty())
    }

    @Test
    fun `maps nested styles safe links quotations and code metadata to native types`() {
        val formatted = formatter.format(
            "code link",
            listOf(
                MessageEntity(0, 9, "quote"),
                MessageEntity(0, 4, "precode", language = "kotlin"),
                MessageEntity(5, 4, "bold"),
                MessageEntity(5, 4, "italic"),
                MessageEntity(5, 4, "texturl", "https://example.com"),
            ),
        )
        assertEquals("kotlin", assertIs<TdApi.TextEntityTypePreCode>(formatted.entities[1].type).language)
        assertTrue(formatted.entities.any { it.type is TdApi.TextEntityTypeTextUrl })
        assertEquals(5, formatted.entities.size)
    }

    @Test
    fun `rejects invalid offsets overflow zero lengths split surrogates and malformed Unicode`() {
        Stream.of(
            MessageEntity(-1, 1, "bold"),
            MessageEntity(0, 0, "bold"),
            MessageEntity(0, Int.MAX_VALUE, "bold"),
            MessageEntity(Int.MAX_VALUE, 1, "bold"),
            MessageEntity(0, 1, "bold"),
            MessageEntity(1, 1, "bold"),
        ).forEach { entity ->
            assertFailsWith<InvalidMessageEntitiesException> { formatter.format("😀", listOf(entity)) }
        }
        assertFailsWith<InvalidMessageEntitiesException> { formatter.format("\uD83D", emptyList()) }
        assertFailsWith<InvalidMessageEntitiesException> { formatter.format("\uDE00", emptyList()) }
    }

    @Test
    fun `rejects unsupported metadata unsafe URLs and excessive entity counts`() {
        Stream.of(
            MessageEntity(0, 1, "customemoji"),
            MessageEntity(0, 1, "mentionname", "tg://user?id=7"),
            MessageEntity(0, 1, "bold", "https://example.com"),
            MessageEntity(0, 1, "italic", language = "kotlin"),
            MessageEntity(0, 1, "precode", language = "a\nb"),
            MessageEntity(0, 1, "texturl", "javascript:alert(1)"),
            MessageEntity(0, 1, "texturl", "file:///secret"),
            MessageEntity(0, 1, "texturl", "https://user:secret@example.com"),
            MessageEntity(0, 1, "texturl", "/relative"),
        ).forEach { entity ->
            assertFailsWith<InvalidMessageEntitiesException> { formatter.format("x", listOf(entity)) }
        }
        assertFailsWith<InvalidMessageEntitiesException> {
            formatter.format("x".repeat(101), List(101) { MessageEntity(it, 1, "bold") })
        }
    }

    @Test
    fun `rejects crossing ranges nested code nested quotes and overlapping duplicate types`() {
        Stream.of(
            listOf(MessageEntity(0, 3, "bold"), MessageEntity(2, 3, "italic")),
            listOf(MessageEntity(0, 5, "bold"), MessageEntity(1, 2, "code")),
            listOf(MessageEntity(0, 5, "code"), MessageEntity(1, 2, "bold")),
            listOf(MessageEntity(0, 5, "quote"), MessageEntity(1, 2, "expandablequote")),
            listOf(MessageEntity(0, 5, "bold"), MessageEntity(1, 2, "bold")),
            listOf(MessageEntity(0, 5, "texturl", "https://example.com"), MessageEntity(1, 2, "quote")),
        ).forEach { entities ->
            assertFailsWith<InvalidMessageEntitiesException> { formatter.format("abcde", entities) }
        }
    }
}
