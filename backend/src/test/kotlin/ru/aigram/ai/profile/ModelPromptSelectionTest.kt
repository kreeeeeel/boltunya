package ru.aigram.ai.profile

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.ai.content.Media
import org.springframework.core.io.ByteArrayResource
import org.springframework.util.MimeTypeUtils
import ru.aigram.ai.ConversationService
import ru.aigram.ai.PromptBuilder
import ru.aigram.ai.research.InternetResearcher
import ru.aigram.ai.research.ResearchFactGuard
import ru.aigram.ai.research.ResearchLinkPolicy
import ru.aigram.ai.research.ResearchPlanner
import ru.aigram.domain.ai.profile.ModelCapabilities
import ru.aigram.domain.ai.profile.ModelProfileUpdate
import ru.aigram.domain.ai.profile.ModelProvider
import ru.aigram.domain.ai.profile.ModelTask
import ru.aigram.domain.ai.profile.ModelTaskRouting
import ru.aigram.domain.ai.prompt.ModelPromptSettings
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatRule
import ru.aigram.resources.ResourceCatalog
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModelPromptSelectionTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `model instructions follow effective selection while the chosen conversation style remains last`() {
        ProfileAiTestServices(directory).use { services ->
            services.prompts.save(ModelPromptSettings("qwen3:1.7b", instructions = "Local model preference"))
            val local = ChatRule(model = "qwen3:1.7b", useDefaultModel = false, style = "business")
            val instructions = services.completion.instructions(local, "Shared instructions")
            assertTrue(instructions.contains("Local model preference"))
            assertTrue(instructions.contains("Shared instructions"))
            assertEquals(
                "Shared instructions",
                services.completion.instructions(local.copy(model = "gemma3:4b"), "Shared instructions"),
            )
            val system = PromptBuilder().build(
                local,
                listOf(ChatMessage("1", "synthetic", "Вопрос", false, 0)),
                instructions,
            ).first().text.orEmpty()
            assertTrue(system.indexOf("Выбранный стиль диалога") > system.indexOf("Local model preference"))
            val profile = services.profiles.save(
                null,
                ModelProfileUpdate(
                    "Remote",
                    ModelProvider.OPENAI_COMPATIBLE,
                    baseUrl = "http://127.0.0.1:1/v1",
                    model = "v1",
                ),
            )
            services.prompts.save(ModelPromptSettings(profile.model, profile.id, "API model preference"))
            val api = local.copy(modelProfileId = profile.id)
            assertTrue(services.completion.instructions(api, "Shared").contains("API model preference"))
            assertFalse(services.completion.instructions(api, "Shared").contains("Local model preference"))
            services.profiles.save(
                profile.id,
                ModelProfileUpdate(
                    "Remote",
                    ModelProvider.OPENAI_COMPATIBLE,
                    baseUrl = "http://127.0.0.1:1/v1",
                    model = "v2",
                ),
            )
            assertEquals("Shared", services.completion.instructions(api, "Shared"))
        }
    }

    @Test
    fun `vision reply uses vision model instructions instead of the routed reply profile instructions`() {
        val response = """
            {
              "id":"test","object":"chat.completion","created":1,"model":"vision-model",
              "choices":[{"index":0,"message":{"role":"assistant","content":"A photo"},"finish_reason":"stop"}],
              "usage":{"prompt_tokens":4,"completion_tokens":2,"total_tokens":6}
            }
        """.trimIndent()
        OpenAiStubServer(response, false).start().use { server ->
            ProfileAiTestServices(directory).use { services ->
                val replyProfile = services.profiles.save(
                    null,
                    ModelProfileUpdate(
                        "Reply",
                        ModelProvider.OPENAI_COMPATIBLE,
                        baseUrl = server.baseUrl,
                        model = "reply-model",
                        capabilities = ModelCapabilities(streaming = false),
                    ),
                )
                val visionProfile = services.profiles.save(
                    null,
                    ModelProfileUpdate(
                        "Vision",
                        ModelProvider.OPENAI_COMPATIBLE,
                        baseUrl = server.baseUrl,
                        model = "vision-model",
                        capabilities = ModelCapabilities(streaming = false, vision = true),
                    ),
                )
                services.routing.save(
                    ModelTaskRouting(
                        enabled = true,
                        replyProfileId = replyProfile.id,
                        visionProfileId = visionProfile.id,
                    ),
                )
                services.prompts.save(
                    ModelPromptSettings(replyProfile.model, replyProfile.id, "Reply-only preference"),
                )
                services.prompts.save(
                    ModelPromptSettings(visionProfile.model, visionProfile.id, "Vision-only preference"),
                )
                val resources = ResourceCatalog()
                val conversation = ConversationService(
                    services.completion,
                    PromptBuilder(),
                    InternetResearcher(),
                    ResearchPlanner(),
                    ResearchLinkPolicy(),
                    ResearchFactGuard(resources),
                    resources,
                    services.tracing,
                )
                val result = conversation.reply(
                    ChatRule(style = "business", internetAccess = false),
                    listOf(ChatMessage("1", "synthetic", "Describe", false, 0, kind = "photo")),
                    globalPrompt = "Shared instructions",
                    images = mapOf(
                        "1" to listOf(Media(MimeTypeUtils.IMAGE_PNG, ByteArrayResource(byteArrayOf(1, 2, 3)))),
                    ),
                    task = ModelTask.VISION,
                )
                val request = assertNotNull(server.body.get())
                val messages = assertNotNull(request["messages"]).jsonArray
                val system = messages.first { it.jsonObject["role"]?.jsonPrimitive?.content == "system" }
                    .jsonObject["content"]?.jsonPrimitive?.content.orEmpty()
                assertEquals("vision-model", request["model"]?.jsonPrimitive?.content)
                assertEquals("vision-model", result.model)
                assertEquals("A photo", result.text)
                assertEquals(1, server.requests.get())
                assertTrue(system.contains("Shared instructions"))
                assertTrue(system.contains("Vision-only preference"))
                assertFalse(system.contains("Reply-only preference"))
                assertTrue(system.indexOf("Выбранный стиль диалога") > system.indexOf("Vision-only preference"))
            }
        }
    }
}
