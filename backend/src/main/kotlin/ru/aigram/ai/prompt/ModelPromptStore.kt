package ru.aigram.ai.prompt

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import ru.aigram.ai.profile.ResolvedModelTarget
import ru.aigram.exception.impl.InvalidModelPromptException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private const val MAX_MODEL_PROMPTS = 200

@Component
class ModelPromptStore @Inject constructor(directory: Path) {
    private val file = directory.resolve("models/prompts.json")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile private var entries: List<ModelPromptSettings> = if (Files.isRegularFile(file)) {
        json.decodeFromString(Files.readString(file))
    } else {
        emptyList()
    }

    fun list(): List<ModelPromptSettings> = entries.toList()

    fun get(target: ResolvedModelTarget): ModelPromptSettings = entries.stream().filter {
        it.model == target.model && it.profileId == target.profileId
    }.findFirst().orElse(ModelPromptSettings(target.model, target.profileId))

    fun configure(target: ResolvedModelTarget): ResolvedModelTarget = configure(target, get(target))

    fun configure(target: ResolvedModelTarget, preferences: ModelPromptSettings): ResolvedModelTarget = target.copy(
        options = target.options.copy(contextWindow = preferences.contextWindow ?: target.options.contextWindow),
        tokenEncoding = preferences.tokenEncoding,
        automaticContext = target.profileId.isEmpty() && preferences.contextWindow == null,
    )

    @Synchronized
    fun save(value: ModelPromptSettings): ModelPromptSettings {
        val remaining = entries.filterNot { it.model == value.model && it.profileId == value.profileId }
        if (remaining.size >= MAX_MODEL_PROMPTS) throw InvalidModelPromptException()
        val updated = remaining + value
        Files.createDirectories(file.parent)
        val temporary = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.writeString(temporary, json.encodeToString(updated))
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        entries = updated
        return value
    }
}
