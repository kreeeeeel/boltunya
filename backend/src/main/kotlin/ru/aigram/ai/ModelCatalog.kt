package ru.aigram.ai

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import ru.aigram.domain.ModelInfo
import ru.aigram.resources.ResourceCatalog

class ModelCatalog @Inject constructor() {
    val models: List<ModelInfo> = Json.decodeFromString(ResourceCatalog().text("models/catalog.json"))

    fun contains(id: String): Boolean = models.stream().anyMatch { it.id == id }

    fun supportsAutomation(id: String): Boolean = models.stream().anyMatch { it.id == id && it.supportsAutomation }

    fun installed(names: Set<String>): List<ModelInfo> = models.stream().map {
        it.copy(installed = it.id in names)
    }.toList()
}
