package ru.aigram.storage

import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import ru.aigram.domain.ai.model.ModelMeasurement
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Component
class ModelMeasurementStore(directory: Path) {
    private val file = directory.resolve("model-measurements.json")
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var measurements = if (Files.exists(file)) {
        json.decodeFromString<Map<String, ModelMeasurement>>(Files.readString(file))
    } else {
        emptyMap()
    }

    fun get(model: String): ModelMeasurement? = measurements[model]

    @Synchronized
    fun record(model: String, measurement: ModelMeasurement) {
        val next = measurements + (model to measurement)
        Files.createDirectories(file.parent)
        val temporary = file.resolveSibling("model-measurements.tmp")
        Files.writeString(temporary, json.encodeToString(next))
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        measurements = next
    }
}
