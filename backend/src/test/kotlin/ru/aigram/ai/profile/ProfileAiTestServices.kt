package ru.aigram.ai.profile

import ru.aigram.ai.ModelInference
import ru.aigram.ai.OllamaRuntime
import ru.aigram.ai.model.ChatCompletionService
import ru.aigram.ai.model.ModelPolicies
import ru.aigram.ai.model.ModelReplyReader
import ru.aigram.ai.model.SpringAiModelFactory
import ru.aigram.automation.activity.ReplyTraceScope
import ru.aigram.configuration.ApplicationConfiguration
import ru.aigram.resources.ResourceCatalog
import ru.aigram.storage.ModelMeasurementStore
import ru.aigram.storage.SettingsStore
import java.nio.file.Path

class ProfileAiTestServices(directory: Path) : AutoCloseable {
    private val resources = ResourceCatalog()
    val profiles = ModelProfileStore(directory, ModelCredentialVault(directory), resources)
    val routing = ModelRoutingStore(directory)
    val settings = SettingsStore(directory)
    val targets = ModelTargetResolver(profiles, routing, settings, ApplicationConfiguration(directory, false))
    private val factory = SpringAiModelFactory()
    private val clients = ProfileModelFactory(profiles, factory)
    val tracing = ReplyTraceScope()
    val completion = ChatCompletionService(
        factory.create("http://127.0.0.1:1"), ModelPolicies(resources), ModelReplyReader(),
        OllamaRuntime("http://127.0.0.1:1"), ModelInference(), ModelMeasurementStore(directory), tracing,
        targets = targets, clients = clients,
    )

    override fun close() {
        clients.close()
    }
}
