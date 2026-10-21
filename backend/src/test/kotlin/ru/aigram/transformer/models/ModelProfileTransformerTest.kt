package ru.aigram.transformer.models

import org.junit.jupiter.api.Test
import ru.aigram.contract.dto.ModelCapabilitiesInput
import ru.aigram.contract.dto.ModelPreset
import ru.aigram.contract.dto.ModelProfileOptionsInput
import ru.aigram.contract.dto.ModelProfileUpdate
import ru.aigram.contract.dto.ModelProvider
import ru.aigram.domain.ai.profile.ModelCapabilities
import ru.aigram.domain.ai.profile.ModelProfile
import ru.aigram.domain.ai.profile.ModelProfileOptions
import ru.aigram.domain.ai.profile.ModelTaskRouting
import ru.aigram.domain.ai.profile.ModelThinking
import ru.aigram.domain.ai.profile.ModelTokenLimit
import java.util.Arrays
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import ru.aigram.domain.ai.profile.ModelProvider as DomainModelProvider

class ModelProfileTransformerTest {
    private val transformer = ModelProfileTransformer()

    @Test
    fun `sampling capabilities and independent local task routes survive typed round trips`() {
        val options = ModelProfileOptions(
            temperature = 0.72, topP = 0.95, topK = 40, presencePenalty = -0.3, frequencyPenalty = 0.4,
            repeatPenalty = 1.15, seed = 42, stop = listOf("END"), maxOutputTokens = 4096,
            contextWindow = 32768, reasoningTokens = 8192, reasoningEffort = "high", thinking = ModelThinking.ENABLED,
            connectTimeoutSeconds = 15, idleTimeoutSeconds = 900, totalTimeoutSeconds = 1800, keepAliveMinutes = 30,
        )
        val capabilities = ModelCapabilities(
            vision = true,
            structuredOutput = true,
            reasoning = true,
            topK = true,
            penalties = true,
            tokenLimit = ModelTokenLimit.MAX_COMPLETION_TOKENS,
        )
        val routing = ModelTaskRouting(
            enabled = true,
            parallelIndependentTasks = true,
            replyModel = "qwen3:4b",
            memoryModel = "gemma3:4b",
            researchModel = "qwen3:1.7b",
            visionModel = "qwen3-vl:4b",
        )

        assertEquals(options, transformer.toDomain(transformer.toContract(options)))
        assertEquals(capabilities, transformer.toDomain(transformer.toContract(capabilities)))
        assertEquals(routing, transformer.toDomain(transformer.toContract(routing)))
    }

    @Test
    fun `profile input credentials never enter the public profile response`() {
        val id = UUID.randomUUID().toString()
        val native = ModelProfile(
            id,
            "Synthetic profile",
            DomainModelProvider.OPENAI_COMPATIBLE,
            "custom",
            "https://example.test/v1",
            "synthetic-model",
            apiKeyConfigured = true,
        )
        val public = transformer.toContract(native)
        val update = transformer.toDomain(
            ModelProfileUpdate(
                name = public.name,
                provider = ModelProvider.OPENAI_COMPATIBLE,
                preset = ModelPreset.CUSTOM,
                baseUrl = public.baseUrl,
                model = public.model,
                apiKey = "synthetic-key",
                clearApiKey = false,
            ),
        )

        assertEquals(id, public.id.toString())
        assertTrue(public.apiKeyConfigured)
        assertFalse(Arrays.stream(public.javaClass.declaredFields).anyMatch { it.name == "apiKey" })
        assertEquals("synthetic-key", update.apiKey)
        assertFalse(update.clearApiKey)
    }

    @Test
    fun `omitted profile input properties use domain defaults without relaxing response requirements`() {
        val input = ModelProfileUpdate(
            name = "Partial profile",
            provider = ModelProvider.OPENAI_COMPATIBLE,
            baseUrl = "https://example.test/v1",
            model = "synthetic-model",
        )

        val result = transformer.toDomain(input)

        assertEquals("custom", result.preset)
        assertTrue(result.enabled)
        assertEquals(ModelProfileOptions(), result.options)
        assertEquals(ModelCapabilities(), result.capabilities)
        assertEquals(null, result.apiKey)
        assertFalse(result.clearApiKey)
    }

    @Test
    fun `partial nested input retains nullable sampling and complete capability defaults`() {
        val options = transformer.toDomain(ModelProfileOptionsInput(contextWindow = 32768, temperature = null))
        val capabilities = transformer.toDomain(ModelCapabilitiesInput(vision = true))

        assertEquals(ModelProfileOptions(contextWindow = 32768), options)
        assertEquals(ModelCapabilities(vision = true), capabilities)
    }
}
