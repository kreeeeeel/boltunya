package ru.aigram.ai.profile

import org.springframework.stereotype.Component
import ru.aigram.ai.ModelCatalog
import ru.aigram.configuration.ApplicationConfiguration
import ru.aigram.domain.ai.profile.ModelCapabilities
import ru.aigram.domain.ai.profile.ModelProvider
import ru.aigram.domain.ai.profile.ModelTask
import ru.aigram.domain.ai.profile.ResolvedModelTarget
import ru.aigram.domain.chat.ChatRule
import ru.aigram.exception.impl.ModelCapabilityUnavailableException
import ru.aigram.exception.impl.ModelProfileDisabledException
import ru.aigram.storage.SettingsStore
import java.net.URI

@Component
class ModelTargetResolver(
    private val profiles: ModelProfileStore,
    private val routing: ModelRoutingStore,
    private val settings: SettingsStore,
    private val configuration: ApplicationConfiguration,
    private val catalog: ModelCatalog = ModelCatalog(),
) {
    fun startupWarmModel(): String? {
        val target = resolve(ModelTask.REPLY, ChatRule())
        return if (target.provider == ModelProvider.OLLAMA &&
            target.baseUrl.trimEnd('/') == configuration.ollamaUrl.trimEnd('/')
        ) {
            target.model
        } else {
            null
        }
    }

    fun resolve(task: ModelTask, rule: ChatRule): ResolvedModelTarget {
        val assignments = routing.get()
        val current = settings.get()
        val resolved = settings.resolveModel(rule)
        val assigned = assignments.enabled && (task != ModelTask.REPLY || rule.useDefaultModel)
        val target = if (assigned && assignments.profileId(task).isNotEmpty()) {
            profile(assignments.profileId(task))
        } else if (assigned && assignments.model(task).isNotEmpty()) {
            localModel(assignments.model(task))
        } else if (task == ModelTask.MEMORY) {
            if (current.defaultModelProfileId.isNotEmpty()) {
                profile(current.defaultModelProfileId)
            } else {
                localModel(current.defaultModel)
            }
        } else if (assignments.enabled && rule.useDefaultModel && assignments.replyProfileId.isNotEmpty()) {
            profile(assignments.replyProfileId)
        } else if (assignments.enabled && rule.useDefaultModel && assignments.replyModel.isNotEmpty()) {
            localModel(assignments.replyModel)
        } else if (resolved.modelProfileId.isNotEmpty()) {
            profile(resolved.modelProfileId)
        } else {
            localModel(resolved.model)
        }
        if (task == ModelTask.VISION && !target.capabilities.vision) {
            throw ModelCapabilityUnavailableException()
        }
        val group = if (!assignments.enabled || !assignments.parallelIndependentTasks) {
            "shared"
        } else {
            if (target.provider == ModelProvider.OLLAMA || !target.remote) {
                "local"
            } else {
                "api:${target.profileId}"
            }
        }
        return target.copy(executionGroup = group)
    }

    fun localModel(id: String): ResolvedModelTarget = ResolvedModelTarget(
        model = id,
        baseUrl = configuration.ollamaUrl,
        remote = !local(configuration.ollamaUrl),
        automaticContext = true,
        capabilities = ModelCapabilities(
            vision = catalog.info(id)?.supportsVision == true,
            structuredOutput = true,
            topK = true,
            penalties = true,
        ),
    )

    fun profile(id: String): ResolvedModelTarget {
        val profile = profiles.get(id)
        if (!profile.enabled) {
            throw ModelProfileDisabledException()
        }
        return ResolvedModelTarget(
            profile.id,
            profile.provider,
            profile.model,
            profile.baseUrl,
            !local(profile.baseUrl),
            profile.capabilities,
            profile.options,
        )
    }

    private fun local(value: String): Boolean {
        val host = URI(value).host.orEmpty().lowercase()
        return host in setOf("localhost", "127.0.0.1", "::1", "[::1]")
    }
}
