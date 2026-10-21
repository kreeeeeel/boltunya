package ru.aigram.ai.profile

import jakarta.inject.Inject
import ru.aigram.configuration.ApplicationConfiguration
import ru.aigram.domain.ChatRule
import ru.aigram.exception.impl.ModelCapabilityUnavailableException
import ru.aigram.exception.impl.ModelProfileDisabledException
import ru.aigram.storage.SettingsStore
import java.net.URI

class ModelTargetResolver @Inject constructor(
    private val profiles: ModelProfileStore,
    private val routing: ModelRoutingStore,
    private val settings: SettingsStore,
    private val configuration: ApplicationConfiguration,
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
        val assigned = if (assignments.enabled && (task != ModelTask.REPLY || rule.useDefaultModel)) {
            assignments.profileId(task)
        } else {
            ""
        }
        val replyProfile = if (assignments.enabled && rule.useDefaultModel) {
            assignments.replyProfileId.ifEmpty { resolved.modelProfileId }
        } else {
            resolved.modelProfileId
        }
        val profileId = assigned.ifEmpty {
            if (task == ModelTask.MEMORY) current.defaultModelProfileId else replyProfile
        }
        val target = if (profileId.isBlank()) {
            ResolvedModelTarget(
                model = if (task == ModelTask.MEMORY) current.defaultModel else resolved.model,
                baseUrl = configuration.ollamaUrl,
                remote = !local(configuration.ollamaUrl),
                capabilities = ModelCapabilities(structuredOutput = true, topK = true, penalties = true),
            )
        } else {
            profile(profileId)
        }
        if (task == ModelTask.VISION && !target.capabilities.vision) throw ModelCapabilityUnavailableException()
        val group = if (!assignments.enabled || !assignments.parallelIndependentTasks) {
            "shared"
        } else {
            if (target.provider == ModelProvider.OLLAMA || !target.remote) "local" else "api:${target.profileId}"
        }
        return target.copy(executionGroup = group)
    }

    fun profile(id: String): ResolvedModelTarget {
        val profile = profiles.get(id)
        if (!profile.enabled) throw ModelProfileDisabledException()
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
