package ru.aigram.ai

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import ru.aigram.domain.ModelInfo
import ru.aigram.resources.ResourceCatalog
import java.util.stream.Stream

@Component
class ModelCatalog @Inject constructor() {
    @Volatile private var observedInstalled: Set<String> = emptySet()
    val models: List<ModelInfo> = Json.decodeFromString(ResourceCatalog().text("models/catalog.json"))
    private val aliases: Map<String, String> = Json.decodeFromString(ResourceCatalog().text("models/aliases.json"))

    fun contains(id: String): Boolean = models.stream().anyMatch { it.id == id }

    fun selectable(id: String): Boolean = info(id) != null || id in observedInstalled

    fun supportsAutomation(id: String): Boolean = info(id)?.supportsAutomation ?: (id in observedInstalled)

    fun info(id: String): ModelInfo? = models.stream().filter { it.id == (aliases[id] ?: id) }.findFirst().orElse(null)

    fun installed(names: Set<String>): List<ModelInfo> {
        observedInstalled = names.toSet()
        return Stream.concat(
            models.stream().map { it.copy(installed = it.id in names) },
            names.stream().filter { !contains(it) }.sorted().map { id ->
                info(id)?.copy(id = id, installed = true) ?: ModelInfo(
                    id = id,
                    name = id,
                    description = "",
                    size = "—",
                    installed = true,
                    family = "custom",
                    speed = "unknown",
                    quality = "unknown",
                    memoryGb = 0,
                    contextTokens = 0,
                    license = "—",
                )
            },
        ).toList()
    }
}
