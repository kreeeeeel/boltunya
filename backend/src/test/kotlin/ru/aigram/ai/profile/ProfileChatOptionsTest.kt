package ru.aigram.ai.profile

import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import org.springframework.ai.ollama.api.OllamaChatOptions
import org.springframework.ai.openai.OpenAiChatOptions
import ru.aigram.exception.impl.ModelCapabilityUnavailableException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ProfileChatOptionsTest {
    private val builder = ProfileChatOptions()

    @Test
    fun `API options omit sampling when unset and use only the selected token limit field`() {
        val target =
            ResolvedModelTarget(
                "id",
                ModelProvider.OPENAI_COMPATIBLE,
                "reasoning-model",
                "https://example.test/v1",
                true,
                ModelCapabilities(tokenLimit = ModelTokenLimit.MAX_COMPLETION_TOKENS, reasoning = true),
                ModelProfileOptions(reasoningTokens = 100, maxOutputTokens = 1000),
            )
        val options = builder.build(target, 200, null) as OpenAiChatOptions
        assertNull(options.temperature)
        assertNull(options.topP)
        assertNull(options.maxTokens)
        assertEquals(300, options.maxCompletionTokens)
        assertNull(options.apiKey)
    }

    @Test
    fun `unsupported provider parameters are omitted while native Ollama preserves them`() {
        val options = ModelProfileOptions(temperature = 0.9, topP = 0.8, topK = 40, repeatPenalty = 1.1)
        val target = ResolvedModelTarget(
            "id",
            ModelProvider.OPENAI_COMPATIBLE,
            "api",
            "https://example.test/v1",
            true,
            ModelCapabilities(temperature = false, topP = false),
            options,
        )
        val remote = builder.build(target, 100, null) as OpenAiChatOptions
        assertNull(remote.temperature)
        assertNull(remote.topP)
        assertNull(remote.topK)
        val native = builder.build(
            target.copy(
                provider = ModelProvider.OLLAMA,
                capabilities = ModelCapabilities(topK = true, penalties = true),
            ),
            100,
            null,
        ) as OllamaChatOptions
        assertEquals(40, native.topK)
        assertEquals(1.1, native.repeatPenalty)
        assertEquals(8192, native.numCtx)
    }

    @Test
    fun `structured output is guarded by model capability`() {
        val target = ResolvedModelTarget(model = "text-only", baseUrl = "http://localhost:11434", remote = false)
        assertFailsWith<ModelCapabilityUnavailableException> { builder.build(target, 100, JsonObject(emptyMap())) }
    }
}
