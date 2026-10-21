package ru.aigram.configuration

import org.junit.jupiter.api.Test
import ru.aigram.contract.dto.Activity
import ru.aigram.contract.dto.ActivityFeedbackUpdate
import ru.aigram.contract.dto.ActivityHistorySettingsInput
import ru.aigram.contract.dto.AppearanceSettingsInput
import ru.aigram.contract.dto.ChatRuleInput
import ru.aigram.contract.dto.ContextExclusionScope
import ru.aigram.contract.dto.ContextExclusionUpdate
import ru.aigram.contract.dto.MemoryRefreshRequest
import ru.aigram.contract.dto.ModelCapabilitiesInput
import ru.aigram.contract.dto.ModelProfileOptionsInput
import ru.aigram.contract.dto.ModelProfileUpdate
import ru.aigram.contract.dto.ModelPromptSettings
import ru.aigram.contract.dto.ModelPromptSettingsInput
import ru.aigram.contract.dto.SettingsUpdate
import ru.aigram.domain.chat.ChatRule
import ru.aigram.transformer.chat.ChatRuleTransformer
import ru.aigram.transformer.models.ModelPromptTransformer
import ru.aigram.transformer.settings.AppearanceTransformer
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import ru.aigram.domain.ai.prompt.ModelPromptSettings as DomainModelPromptSettings
import ru.aigram.domain.settings.AppearanceSettings as DomainAppearanceSettings

class ApiJsonConfigurationTest {
    private fun mapper(): JsonMapper {
        val builder = JsonMapper.builder().addModule(KotlinModule.Builder().build())
        ApiJsonConfiguration().strictApiJson().customize(builder)
        return builder.build()
    }

    @Test
    fun `absent patch fields remain absent while explicit null for nonnullable fields is rejected`() {
        val json = mapper()
        val omitted = json.readValue("{}", SettingsUpdate::class.java)

        assertNull(omitted.paused)
        assertNull(omitted.defaultModel)
        assertNull(omitted.replyDelaySeconds)
        listOf(
            """{"paused":null}""",
            """{"defaultModel":null}""",
            """{"replyDelaySeconds":null}""",
            """{"customStyles":null}""",
        ).stream().forEach { payload ->
            assertFailsWith<JacksonException> { json.readValue(payload, SettingsUpdate::class.java) }
        }
    }

    @Test
    fun `booleans integers and strings retain their declared JSON token types`() {
        val json = mapper()
        listOf(
            """{"paused":"true"}""",
            """{"paused":1}""",
            """{"paused":""}""",
            """{"replyDelaySeconds":"3"}""",
            """{"replyDelaySeconds":1.5}""",
            """{"replyDelaySeconds":true}""",
            """{"defaultModel":42}""",
            """{"defaultModel":true}""",
        ).stream().forEach { payload ->
            assertFailsWith<JacksonException> { json.readValue(payload, SettingsUpdate::class.java) }
        }
        val valid = json.readValue("""{"paused":false,"replyDelaySeconds":3}""", SettingsUpdate::class.java)
        assertEquals(false, valid.paused)
        assertEquals(3, valid.replyDelaySeconds)
    }

    @Test
    fun `schema nullable properties accept explicit null without making all patch properties nullable`() {
        val json = mapper()
        val prompts = json.readValue(
            """{"model":"gemma3:4b","contextWindow":null}""",
            ModelPromptSettings::class.java,
        )
        val activity = json.readValue(
            """
            {"id":"event","chatId":"42","title":"Synthetic chat","status":"ready","detail":"",
             "date":1,"durationMs":1,"feedbackRating":null}
            """.trimIndent(),
            Activity::class.java,
        )
        assertNull(prompts.contextWindow)
        assertNull(activity.feedbackRating)
        assertNull(json.readValue("""{"rating":"good"}""", ActivityFeedbackUpdate::class.java).comment)
        assertEquals(
            "",
            json.readValue("""{"rating":"good","comment":""}""", ActivityFeedbackUpdate::class.java).comment,
        )
        assertFailsWith<JacksonException> {
            json.readValue("""{"rating":"good","comment":null}""", ActivityFeedbackUpdate::class.java)
        }
    }

    @Test
    fun `partial chat request uses complete domain defaults without making response fields optional`() {
        val json = mapper()
        val transformer = ChatRuleTransformer()
        val empty = json.readValue("{}", ChatRuleInput::class.java)
        val partial = json.readValue("""{"enabled":true,"instructions":"Keep it brief"}""", ChatRuleInput::class.java)

        assertEquals(ChatRule(), transformer.toDomain(empty))
        assertEquals(ChatRule(enabled = true, instructions = "Keep it brief"), transformer.toDomain(partial))
        listOf("""{"enabled":null}""", """{"style":null}""", """{"contextMessages":null}""").stream()
            .forEach { payload ->
                assertFailsWith<JacksonException> { json.readValue(payload, ChatRuleInput::class.java) }
            }
    }

    @Test
    fun `prompt and appearance inputs retain defaults and explicit nullable context override`() {
        val json = mapper()
        val prompts = json.readValue(
            """{"model":"qwen3:1.7b","contextWindow":null}""",
            ModelPromptSettingsInput::class.java,
        )
        val appearance = json.readValue("{}", AppearanceSettingsInput::class.java)

        assertEquals(
            DomainModelPromptSettings("qwen3:1.7b"),
            ModelPromptTransformer(ChatRuleTransformer()).toDomain(prompts),
        )
        assertEquals(
            DomainAppearanceSettings(),
            AppearanceTransformer().toDomain(appearance),
        )
        assertFailsWith<JacksonException> {
            json.readValue("""{"tokens":null}""", AppearanceSettingsInput::class.java)
        }
    }

    @Test
    fun `memory refresh defaults remain explicit false and invalid primitive tokens fail`() {
        val json = mapper()
        val request = json.readValue("{}", MemoryRefreshRequest::class.java)

        assertEquals(false, request.rebuild)
        assertEquals(false, request.immediate)
        assertFailsWith<JacksonException> {
            json.readValue("""{"rebuild":"false"}""", MemoryRefreshRequest::class.java)
        }
    }

    @Test
    fun `history storage partial request retains unlimited defaults`() {
        val json = mapper()
        val omitted = json.readValue("{}", ActivityHistorySettingsInput::class.java)
        val partial = json.readValue("""{"retentionDays":30}""", ActivityHistorySettingsInput::class.java)

        assertEquals(0, omitted.retentionDays)
        assertEquals(0, omitted.maxStorageMb)
        assertEquals(30, partial.retentionDays)
        assertEquals(0, partial.maxStorageMb)
        assertFailsWith<JacksonException> {
            json.readValue("""{"maxStorageMb":null}""", ActivityHistorySettingsInput::class.java)
        }
    }

    @Test
    fun `omitted profile objects get defaults while explicit null objects fail and null key is retained`() {
        val json = mapper()
        val required = """
            "name":"Synthetic","provider":"openai-compatible",
            "baseUrl":"https://example.test/v1","model":"test"
        """.trimIndent()
        val profile = json.readValue("{$required,\"apiKey\":null}", ModelProfileUpdate::class.java)

        assertEquals(ModelProfileOptionsInput(), profile.options)
        assertEquals(ModelCapabilitiesInput(), profile.capabilities)
        assertNull(profile.apiKey)
        listOf("options", "capabilities").stream().forEach { name ->
            assertFailsWith<JacksonException> {
                json.readValue("{$required,\"$name\":null}", ModelProfileUpdate::class.java)
            }
        }
    }

    @Test
    fun `omitted context exclusion scope defaults to a single message and explicit null fails`() {
        val json = mapper()
        val omitted = json.readValue("{}", ContextExclusionUpdate::class.java)

        assertEquals(ContextExclusionScope.MESSAGE, omitted.scope)
        assertEquals(true, omitted.excluded)
        listOf("""{"scope":null}""", """{"excluded":null}""").stream().forEach { payload ->
            assertFailsWith<JacksonException> { json.readValue(payload, ContextExclusionUpdate::class.java) }
        }
    }
}
