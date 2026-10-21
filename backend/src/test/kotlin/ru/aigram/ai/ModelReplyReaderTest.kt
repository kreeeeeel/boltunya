package ru.aigram.ai

import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatGenerationMetadata
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

    @Test
    fun `provider reasoning fields never become final text including when the provider has no answer`() {
        listOf("thinking", "reasoningContent").stream().forEach { key ->
            listOf("Готовый ответ", "").stream().forEach { answer ->
                val response = ChatResponse(
                    listOf(
                        Generation(
                            AssistantMessage.builder().content(answer)
                                .properties(mapOf(key to "Internal provider reasoning")).build(),
                        ),
                    ),
                )
                val result = reader.read(response, 100)
                assertEquals(answer, result.text)
                assertEquals(answer, result.rawText)
                assertEquals("Internal provider reasoning", result.thinking)
            }
        }
    }

    @Test
    fun `generation level thinking is retained without classifying ordinary final wording`() {
        val answer = "Думаю, завтра будет удобно."
        val response = ChatResponse(
            listOf(
                Generation(
                    AssistantMessage.builder().content(answer).build(),
                    ChatGenerationMetadata.builder().metadata("thinking", "Internal reasoning").build(),
                ),
            ),
        )
        val result = reader.read(response, 100)
        assertEquals(answer, result.text)
        assertEquals("Internal reasoning", result.thinking)
    }
}
