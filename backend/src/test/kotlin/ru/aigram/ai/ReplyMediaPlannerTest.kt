package ru.aigram.ai

import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import ru.aigram.domain.MediaAttachment
import ru.aigram.domain.StickerItem
import ru.aigram.exception.impl.InvalidModelReplyException
import ru.aigram.resources.ResourceCatalog
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReplyMediaPlannerTest {
    private val planner = ReplyMediaPlanner(ResourceCatalog())
    private val stickers = listOf(StickerItem("7", "🙂", "1", MediaAttachment(7, "image/webp", "sticker"), 100, 100))

    @Test
    fun `one structured reply can contain normal text an allowed sticker or both`() {
        assertEquals(ModelReplyPlan("Привет", "7"), planner.read("""{"text":"Привет","stickerId":"7"}""", stickers))
        assertEquals(ModelReplyPlan("", "7"), planner.read("""{"text":"","stickerId":"7"}""", stickers))
        assertEquals(ModelReplyPlan("Привет", ""), planner.read("""{"text":"Привет","stickerId":""}""", stickers))
        assertTrue(planner.schema(stickers)["properties"]?.jsonObject?.containsKey("stickerId") == true)
        val messages = planner.messages(listOf(SystemMessage("Selected style"), UserMessage("Hello")), stickers)
        assertTrue(messages.first().text.orEmpty().contains("Selected style"))
        assertTrue(messages.first().text.orEmpty().contains("🙂"))
        assertEquals("Hello", messages.last().text)
    }

    @Test
    fun `never accepts invented sticker IDs executable arguments or malformed JSON`() {
        listOf(
            """{"text":"Hi","stickerId":"8"}""",
            """{"text":"Hi","stickerId":"7","command":"send-file"}""",
            "plain text",
        ).stream().forEach { output ->
            assertFailsWith<InvalidModelReplyException> { planner.read(output, stickers) }
        }
    }
}
