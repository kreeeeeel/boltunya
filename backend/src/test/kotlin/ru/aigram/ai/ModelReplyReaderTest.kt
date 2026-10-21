package ru.aigram.ai

import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import ru.aigram.ai.model.ModelReplyReader
import kotlin.test.assertEquals

class ModelReplyReaderTest {
    private val reader = ModelReplyReader()

    @Test
    fun readsFinalTextWithoutLegacyThinkingTags() {
        listOf("think", "thinking", "reasoning").stream().forEach { tag ->
            val response = ChatResponse(
                listOf(
                    Generation(
                        AssistantMessage.builder()
                            .content("<$tag>private thoughts</$tag>Привет!").build(),
                    ),
                ),
            )
            assertEquals("Привет!", reader.read(response, 100).text)
        }
        val truncated = ChatResponse(
            listOf(
                Generation(
                    AssistantMessage.builder()
                        .content("<think>unfinished private thoughts").build(),
                ),
            ),
        )
        assertEquals("", reader.read(truncated, 100).text)
    }
}
