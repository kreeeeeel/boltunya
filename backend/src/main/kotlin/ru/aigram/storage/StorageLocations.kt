package ru.aigram.storage

import kotlinx.serialization.json.Json
import ru.aigram.ai.OllamaInstallation
import ru.aigram.configuration.ApplicationConfiguration
import ru.aigram.resources.ResourceCatalog
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

private val LOCAL_HOSTS = setOf("127.0.0.1", "localhost", "::1", "[::1]")

class StorageLocations(
    private val configuration: ApplicationConfiguration,
    private val installation: OllamaInstallation,
    resources: ResourceCatalog,
    private val environment: Map<String, String> = System.getenv(),
    private val userDirectory: Path = Path.of(System.getProperty("user.home")),
) {
    private val labels = Json.decodeFromString<Map<String, String>>(
        resources.text("diagnostics/ru/storage.json"),
    )

    fun report(): StorageLocationReport {
        val root = configuration.directory
        val localServer = URI.create(configuration.ollamaUrl).host in LOCAL_HOSTS
        val runtime = if (localServer) installation.executable()?.parent else null
        val configuredModels = environment.entries.firstOrNull { it.key.equals("OLLAMA_MODELS", true) }
            ?.value?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
        val models = if (localServer && (runtime != null || configuredModels != null)) {
            configuredModels ?: userDirectory.resolve(".ollama/models")
        } else {
            null
        }
        return StorageLocationReport(
            listOf(
                directory("application", root),
                directory("memory", root.resolve("memory")),
                directory("telegram-cache", root.resolve("telegram")),
                runtime?.let { directory("ollama-runtime", it) } ?: external("ollama-runtime"),
                models?.let { directory("ollama-models", it, labels.getValue("models-note")) }
                    ?: external("ollama-models"),
                directory("diagnostics", root.resolve("diagnostics")),
            ),
        )
    }

    private fun directory(id: String, path: Path, note: String = ""): StorageLocation {
        val normalized = path.toAbsolutePath().normalize()
        val exists = Files.isDirectory(normalized)
        return StorageLocation(id, labels.getValue(id), normalized.toString(), exists, exists, note = note)
    }

    private fun external(id: String): StorageLocation = StorageLocation(
        id,
        labels.getValue(id),
        "",
        available = false,
        openable = false,
        kind = "external",
        note = labels.getValue("external-note"),
    )
}
