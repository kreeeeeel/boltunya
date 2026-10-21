package ru.aigram.ai.profile

import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import ru.aigram.domain.ai.profile.ModelTaskRouting
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Component
class ModelRoutingStore(directory: Path) {
    private val file = directory.resolve("models/routing.json")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile private var routing = if (Files.isRegularFile(file)) {
        json.decodeFromString<ModelTaskRouting>(Files.readString(file))
    } else {
        ModelTaskRouting()
    }

    fun get(): ModelTaskRouting = routing

    @Synchronized
    fun save(next: ModelTaskRouting): ModelTaskRouting {
        Files.createDirectories(file.parent)
        val temporary = file.resolveSibling("routing.tmp")
        Files.writeString(temporary, json.encodeToString(next))
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        routing = next
        return next
    }
}
