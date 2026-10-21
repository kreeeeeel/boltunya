package ru.boltunya.ai.profile

import ru.boltunya.ai.ModelInference
import ru.boltunya.ai.OllamaRuntime
import ru.boltunya.ai.model.ChatCompletionService
import ru.boltunya.ai.model.ModelPolicies
import ru.boltunya.ai.model.ModelReplyReader
import ru.boltunya.ai.model.SpringAiModelFactory
import ru.boltunya.automation.activity.ReplyTraceScope
import ru.boltunya.configuration.ApplicationConfiguration
import ru.boltunya.resources.ResourceCatalog
import ru.boltunya.storage.ModelMeasurementStore
import ru.boltunya.storage.SettingsStore
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
