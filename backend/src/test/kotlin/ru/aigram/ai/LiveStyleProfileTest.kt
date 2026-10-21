package ru.aigram.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.memory.MemoryContext
import ru.aigram.resources.ResourceCatalog
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "TIHO_LIVE_AI", matches = "true")
class LiveStyleProfileTest {
    @Test
    fun `abstract voice preserves substantive answers without copying the sample topics`() {
        LiveResearchFixtures().use { fixtures ->
            val services = AiTestServices(prompts = PromptBuilder(clock = fixtures.clock))
            val resources = ResourceCatalog()
            val samples = Json.decodeFromString<List<String>>(resources.text("testing/abstract-style.json"))
                .mapIndexed { index, text -> ChatMessage("style-$index", "style", text, true, 0) }
            val cases = Json.parseToJsonElement(resources.text("testing/model-quality.json")).jsonArray
                .filter { it.jsonObject.getValue("id").jsonPrimitive.content in setOf("recipe", "meeting-context") }
            val failures = mutableListOf<String>()
            val report = buildJsonArray {
                cases.stream().forEach { element ->
                    val case = element.jsonObject
                    val reply = services.reply(
                        ChatRule(
                            model = "gemma3:4b",
                            maxTokens = 128,
                            creativity = 20,
                            emoji = false,
                            internetAccess = false,
                        ),
                        Json.decodeFromJsonElement<List<ChatMessage>>(case.getValue("history")),
                        styleHistory = samples,
                        memory = MemoryContext(
                            ownerSummary = "Пишет коротко, со строчной буквы, без официальных оборотов.",
                        ),
                    )
                    val valid = ReplyExpectation().checks(case.getValue("expected").jsonObject, reply.text).all { it }
                    if (!valid) {
                        failures.add(case.getValue("id").jsonPrimitive.content)
                    }
                    add(
                        buildJsonObject {
                            put("case", case.getValue("id").jsonPrimitive.content)
                            put("reply", reply.text)
                            put("passed", valid)
                        },
                    )
                }
            }
            val file = Path.of("build/reports/abstract-style.json")
            Files.createDirectories(file.parent)
            Files.writeString(file, Json.encodeToString(report))
            services.runtime.releaseModels()
            assertTrue(failures.isEmpty(), failures.joinToString())
        }
    }
}
