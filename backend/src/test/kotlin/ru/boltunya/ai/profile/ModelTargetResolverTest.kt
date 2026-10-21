package ru.boltunya.ai.profile

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.configuration.ApplicationConfiguration
import ru.boltunya.domain.ChatRule
import ru.boltunya.exception.impl.ModelCapabilityUnavailableException
import ru.boltunya.resources.ResourceCatalog
import ru.boltunya.storage.SettingsStore
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertNull

class ModelTargetResolverTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `explicit chat profile wins over global reply assignment while memory has its own model`() {
        val profiles = ModelProfileStore(directory, ModelCredentialVault(directory), ResourceCatalog())
        fun profile(name: String) = profiles.save(
            null,
            ModelProfileUpdate(
                name,
                ModelProvider.OPENAI_COMPATIBLE,
                baseUrl = "https://example.test/v1",
                model = name,
            ),
        )
        val main = profile("main")
        val reply = profile("reply")
        val memory = profile("memory")
        val chat = profile("chat")
        val settings = SettingsStore(directory)
        settings.update { it.copy(defaultModelProfileId = main.id) }
        val routing = ModelRoutingStore(directory)
        routing.save(ModelTaskRouting(true, true, reply.id, memory.id))
        val targets = ModelTargetResolver(profiles, routing, settings, ApplicationConfiguration(directory, false))
        assertEquals(reply.id, targets.resolve(ModelTask.REPLY, ChatRule()).profileId)
        assertEquals(reply.id, targets.resolve(ModelTask.RESEARCH, ChatRule()).profileId)
        assertEquals(
            chat.id,
            targets.resolve(ModelTask.REPLY, ChatRule(modelProfileId = chat.id, useDefaultModel = false)).profileId,
        )
        assertEquals(
            memory.id,
            targets.resolve(ModelTask.MEMORY, ChatRule(modelProfileId = chat.id, useDefaultModel = false)).profileId,
        )
        assertEquals(
            chat.id,
            targets.resolve(ModelTask.RESEARCH, ChatRule(modelProfileId = chat.id, useDefaultModel = false)).profileId,
        )
        assertTrue(targets.resolve(ModelTask.REPLY, ChatRule()).remote)
    }

    @Test
    fun `disabled routing preserves legacy settings and all local profiles share one execution group`() {
        val profiles = ModelProfileStore(directory, ModelCredentialVault(directory), ResourceCatalog())
        val local = profiles.save(
            null,
            ModelProfileUpdate("Local", ModelProvider.OLLAMA, "ollama", "http://127.0.0.1:11434", "local:test"),
        )
        val settings = SettingsStore(directory)
        val routing = ModelRoutingStore(directory)
        routing.save(ModelTaskRouting(enabled = false, parallelIndependentTasks = true, replyProfileId = local.id))
        val targets = ModelTargetResolver(profiles, routing, settings, ApplicationConfiguration(directory, false))
        val legacy = targets.resolve(ModelTask.REPLY, ChatRule())
        assertEquals("", legacy.profileId)
        assertEquals(settings.get().defaultModel, legacy.model)
        assertEquals(
            legacy.executionGroup,
            targets.resolve(
                ModelTask.REPLY,
                ChatRule(modelProfileId = local.id, useDefaultModel = false),
            ).executionGroup,
        )
    }

    @Test
    fun `startup warms only the effective reply model served by the application engine`() {
        val profiles = ModelProfileStore(directory, ModelCredentialVault(directory), ResourceCatalog())
        val settings = SettingsStore(directory)
        val routing = ModelRoutingStore(directory)
        val configuration = ApplicationConfiguration(directory, false)
        val targets = ModelTargetResolver(profiles, routing, settings, configuration)
        assertEquals(settings.get().defaultModel, targets.startupWarmModel())
        val api = profiles.save(null, ModelProfileUpdate(
            "Remote API", ModelProvider.OPENAI_COMPATIBLE, baseUrl = "https://example.test/v1", model = "remote",
        ))
        routing.save(ModelTaskRouting(enabled = true, replyProfileId = api.id))
        assertNull(targets.startupWarmModel())
        val local = profiles.save(null, ModelProfileUpdate(
            "Selected local", ModelProvider.OLLAMA, "ollama", configuration.ollamaUrl, "local:selected",
        ))
        routing.save(ModelTaskRouting(enabled = true, replyProfileId = local.id))
        assertEquals("local:selected", targets.startupWarmModel())
        val elsewhere = profiles.save(null, ModelProfileUpdate(
            "Other Ollama", ModelProvider.OLLAMA, "ollama", "http://elsewhere.test:11434", "other:model",
        ))
        routing.save(ModelTaskRouting(enabled = true, replyProfileId = elsewhere.id))
        assertNull(targets.startupWarmModel())
        routing.save(ModelTaskRouting())
        settings.update { it.copy(defaultModelProfileId = api.id) }
        assertNull(targets.startupWarmModel())
    }

    @Test
    fun `vision is explicit and unsupported fallback fails before uploading an image`() {
        val profiles = ModelProfileStore(directory, ModelCredentialVault(directory), ResourceCatalog())
        val settings = SettingsStore(directory)
        val targets =
            ModelTargetResolver(
                profiles,
                ModelRoutingStore(directory),
                settings,
                ApplicationConfiguration(directory, false),
            )
        assertFailsWith<ModelCapabilityUnavailableException> { targets.resolve(ModelTask.VISION, ChatRule()) }
    }
}
