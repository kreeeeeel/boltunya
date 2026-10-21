package ru.aigram.ai.model

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import ru.aigram.resources.ResourceCatalog

@Component
class ModelPolicies @Inject constructor(resources: ResourceCatalog) {
    private val policies = Json.decodeFromString<Map<String, ModelPolicy>>(resources.text("models/inference.json"))

    fun get(model: String): ModelPolicy = policies[model] ?: ModelPolicy()
}
