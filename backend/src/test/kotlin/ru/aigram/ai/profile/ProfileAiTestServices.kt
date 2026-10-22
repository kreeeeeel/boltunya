package ru.aigram.ai.profile

import ru.aigram.ai.ModelInference
import ru.aigram.ai.OllamaRuntime
import ru.aigram.ai.model.ChatCompletionService
import ru.aigram.ai.model.ModelPolicies
import ru.aigram.ai.model.ModelReplyReader
import ru.aigram.ai.model.SpringAiModelFactory
import ru.aigram.ai.prompt.ModelPromptStore
import ru.aigram.automation.activity.ReplyTraceScope
import ru.aigram.configuration.ApplicationConfiguration
import ru.aigram.resources.ResourceCatalog
import ru.aigram.storage.ModelMeasurementStore
import ru.aigram.storage.SettingsStore
import java.nio.file.Path

class ProfileAiTestServices(directory: Path, baseUrl: String = "http://127.0.0.1:1") : AutoCloseable {
    private val resources = ResourceCatalog()
    val profiles = ModelProfileStore(directory, ModelCredentialVault(directory), resources)
    val prompts = ModelPromptStore(directory)
    val routing = ModelRoutingStore(directory)
    val settings = SettingsStore(directory)
    val targets = ModelTargetResolver(profiles, routing, settings, ApplicationConfiguration(directory, false, baseUrl))
    private val factory = SpringAiModelFactory()
    private val clients = ProfileModelFactory(profiles, factory)
    val tracing = ReplyTraceScope()
    val runtime = OllamaRuntime(baseUrl)
    val completion = ChatCompletionService(
        factory.create(baseUrl), ModelPolicies(resources), ModelReplyReader(),
        runtime, ModelInference(), ModelMeasurementStore(directory), tracing,
        targets = targets, clients = clients, promptSettings = prompts,
    )

    override fun close() {
        clients.close()
    }
}
