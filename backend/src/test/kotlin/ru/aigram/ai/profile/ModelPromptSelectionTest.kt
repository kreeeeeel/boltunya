package ru.aigram.ai.profile

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.PromptBuilder
import ru.aigram.ai.prompt.ModelPromptSettings
import ru.aigram.domain.ChatMessage
import ru.aigram.domain.ChatRule
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
}
