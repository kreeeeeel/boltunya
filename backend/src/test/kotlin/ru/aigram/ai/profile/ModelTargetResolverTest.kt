package ru.aigram.ai.profile

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.configuration.ApplicationConfiguration
import ru.aigram.domain.ai.profile.ModelProfileUpdate
import ru.aigram.domain.ai.profile.ModelProvider
import ru.aigram.domain.ai.profile.ModelTask
import ru.aigram.domain.ai.profile.ModelTaskRouting
import ru.aigram.domain.chat.ChatRule
import ru.aigram.exception.impl.ModelCapabilityUnavailableException
import ru.aigram.resources.ResourceCatalog
import ru.aigram.storage.SettingsStore
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        val api = profiles.save(
            null,
            ModelProfileUpdate(
                "Remote API",
                ModelProvider.OPENAI_COMPATIBLE,
                baseUrl = "https://example.test/v1",
                model = "remote",
            ),
        )
        routing.save(ModelTaskRouting(enabled = true, replyProfileId = api.id))
        assertNull(targets.startupWarmModel())
        val local = profiles.save(
            null,
            ModelProfileUpdate(
                "Selected local",
                ModelProvider.OLLAMA,
                "ollama",
                configuration.ollamaUrl,
                "local:selected",
            ),
        )
        routing.save(ModelTaskRouting(enabled = true, replyProfileId = local.id))
        assertEquals("local:selected", targets.startupWarmModel())
        val elsewhere = profiles.save(
            null,
            ModelProfileUpdate(
                "Other Ollama",
                ModelProvider.OLLAMA,
                "ollama",
                "http://elsewhere.test:11434",
                "other:model",
            ),
        )
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
        assertFailsWith<ModelCapabilityUnavailableException> {
            targets.resolve(ModelTask.VISION, ChatRule(model = "qwen3:1.7b", useDefaultModel = false))
        }
    }

    @Test
    fun `persisted native task assignments override a global API while explicit chat replies keep their model`() {
        ProfileAiTestServices(directory).use { services ->
            val api = services.profiles.save(
                null,
                ModelProfileUpdate(
                    "Global API",
                    ModelProvider.OPENAI_COMPATIBLE,
                    baseUrl = "https://example.test/v1",
                    model = "api-model",
                ),
            )
            services.settings.update { it.copy(defaultModelProfileId = api.id) }
            val assigned = ModelTaskRouting(
                enabled = true,
                parallelIndependentTasks = true,
                replyModel = "qwen3:1.7b",
                memoryModel = "gemma3:4b",
                researchModel = "qwen2.5:7b",
                visionModel = "qwen3-vl:2b",
            )
            services.routing.save(assigned)
            assertEquals(assigned, ModelRoutingStore(directory).get())
            ModelTask.entries.stream().forEach { task ->
                val target = services.targets.resolve(task, ChatRule())
                assertEquals(assigned.model(task), target.model)
                assertEquals("", target.profileId)
                assertEquals(ModelProvider.OLLAMA, target.provider)
                assertEquals("local", target.executionGroup)
            }
            val explicit = ChatRule(model = "gemma3:12b", useDefaultModel = false)
            assertEquals("gemma3:12b", services.targets.resolve(ModelTask.REPLY, explicit).model)
            assertEquals("gemma3:4b", services.targets.resolve(ModelTask.MEMORY, explicit).model)
            services.routing.save(ModelTaskRouting(enabled = true, replyModel = "qwen3:1.7b"))
            assertEquals("qwen3:1.7b", services.targets.resolve(ModelTask.RESEARCH, ChatRule()).model)
            assertEquals(api.id, services.targets.resolve(ModelTask.MEMORY, ChatRule()).profileId)
            services.routing.save(assigned.copy(enabled = false))
            assertEquals(api.id, services.targets.resolve(ModelTask.REPLY, ChatRule()).profileId)
        }
    }

    @Test
    fun `only confirmed native vision variants may receive an image`() {
        ProfileAiTestServices(directory).use { services ->
            services.routing.save(ModelTaskRouting(enabled = true, visionModel = "gemma3:1b"))
            assertFailsWith<ModelCapabilityUnavailableException> {
                services.targets.resolve(ModelTask.VISION, ChatRule())
            }
            services.routing.save(ModelTaskRouting(enabled = true, visionModel = "gemma3:latest"))
            assertTrue(services.targets.resolve(ModelTask.VISION, ChatRule()).capabilities.vision)
            services.routing.save(ModelTaskRouting(enabled = true, visionModel = "qwen3.5:4b"))
            assertTrue(services.targets.resolve(ModelTask.VISION, ChatRule()).capabilities.vision)
            services.routing.save(ModelTaskRouting(enabled = true, visionModel = "custom:unknown"))
            assertFailsWith<ModelCapabilityUnavailableException> {
                services.targets.resolve(ModelTask.VISION, ChatRule())
            }
        }
    }
}
