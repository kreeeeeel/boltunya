package ru.aigram.speech

import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import ru.aigram.domain.speech.SpeechAssetKind
import ru.aigram.exception.impl.InvalidSpeechModelImportException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator
import java.util.UUID

private const val MAX_REGISTRY_BYTES = 1024L * 1024
private const val MAX_CUSTOM_MODELS = 128
private const val CUSTOM_PREFIX = "custom-"

@Component
class SpeechCustomModelRegistry(directory: Path, private val json: Json) {
    private val directory = directory.resolve("speech").toAbsolutePath().normalize()
    private val file = this.directory.resolve("custom-models.json")
    private var models = load()

    @Synchronized
    fun list(): List<SpeechAssetDefinition> = models.toList()

    @Synchronized
    fun publish(definition: SpeechAssetDefinition, staging: Path) {
        if (!valid(definition) || models.size >= MAX_CUSTOM_MODELS ||
            models.stream().anyMatch { it.asset.id == definition.asset.id } ||
            staging.parent != directory || !Files.isDirectory(staging, LinkOption.NOFOLLOW_LINKS)
        ) {
            throw InvalidSpeechModelImportException()
        }
        val destination = directory.resolve(definition.asset.id)
        val temporary = directory.resolve(".custom-models-${UUID.randomUUID()}.json")
        val next = models + definition
        val bytes = json.encodeToString(next).toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_REGISTRY_BYTES) {
            throw InvalidSpeechModelImportException()
        }
        var published = false
        try {
            Files.write(temporary, bytes)
            Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE)
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (exception: Exception) {
                Files.walk(destination).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                }
                throw exception
            }
            models = next
            published = true
        } finally {
            Files.deleteIfExists(temporary)
            if (!published && Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) {
                Files.walk(staging).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                }
            }
        }
    }

    private fun load(): List<SpeechAssetDefinition> {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return emptyList()
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_REGISTRY_BYTES) {
            throw InvalidSpeechModelImportException()
        }
        val definitions = json.decodeFromString<List<SpeechAssetDefinition>>(Files.readString(file))
        if (definitions.size > MAX_CUSTOM_MODELS ||
            definitions.stream().anyMatch { !valid(it) } ||
            definitions.stream().map { it.asset.id }.distinct().count() != definitions.size.toLong()
        ) {
            throw InvalidSpeechModelImportException()
        }
        return definitions
    }

    private fun valid(definition: SpeechAssetDefinition): Boolean {
        val id = definition.asset.id
        if (!id.startsWith(CUSTOM_PREFIX)) {
            return false
        }
        val canonical = try {
            UUID.fromString(id.removePrefix(CUSTOM_PREFIX)).toString() == id.removePrefix(CUSTOM_PREFIX)
        } catch (exception: IllegalArgumentException) {
            false
        }
        return canonical && definition.asset.userProvided && definition.asset.kind == SpeechAssetKind.SYNTHESIS &&
            definition.downloadUrl.isEmpty() && definition.archiveRoot.isEmpty() &&
            definition.asset.sourceUrl.isEmpty() && definition.asset.dependencies == listOf("sherpa-runtime") &&
            definition.modelFile.endsWith(".onnx", ignoreCase = true) &&
            definition.modelFile.isNotEmpty() && !definition.modelFile.contains('/') &&
            !definition.modelFile.contains('\\') && !definition.modelFile.contains(':') &&
            !definition.modelFile.contains("..") &&
            definition.requiredFiles == listOf(
                definition.modelFile,
                "tokens.txt",
                "espeak-ng-data/phondata",
                "espeak-ng-data/phonindex",
                "espeak-ng-data/phontab",
            ) && definition.sha256.length == 64 &&
            definition.sha256.chars().allMatch { it in '0'.code..'9'.code || it in 'a'.code..'f'.code }
    }
}
