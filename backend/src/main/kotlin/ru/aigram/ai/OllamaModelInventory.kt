package ru.aigram.ai

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import ru.aigram.validation.InputRule
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path

private const val MANIFEST_DEPTH = 4

class OllamaModelInventory(
    private val root: Path = Path.of(
        System.getenv("OLLAMA_MODELS") ?: "${System.getProperty("user.home")}/.ollama/models",
    ),
) {
    fun installed(): Set<String> {
        val manifests = root.resolve("manifests")
        if (!Files.isDirectory(manifests)) {
            return emptySet()
        }
        return try {
            Files.walk(manifests, MANIFEST_DEPTH).use { files ->
                files.filter { Files.isRegularFile(it) && manifests.relativize(it).nameCount == MANIFEST_DEPTH }
                    .filter {
                        manifests.relativize(it).getName(0).toString() == "registry.ollama.ai" &&
                            !it.fileName.toString().endsWith(".partial") && complete(it)
                    }
                    .map {
                        val relative = manifests.relativize(it)
                        val namespace = relative.getName(1).toString()
                        val model = relative.getName(2).toString() + ":" + relative.fileName
                        if (namespace == "library") {
                            model
                        } else {
                            "$namespace/$model"
                        }
                    }.toList().toSet()
            }
        } catch (exception: IOException) {
            System.err.println("Local model inventory unavailable: ${exception.javaClass.simpleName}")
            emptySet()
        } catch (exception: UncheckedIOException) {
            System.err.println("Local model inventory changed during scan: ${exception.javaClass.simpleName}")
            emptySet()
        }
    }

    private fun complete(manifest: Path): Boolean = try {
        val document = Json.parseToJsonElement(Files.readString(manifest)) as? JsonObject
        val config = document?.get("config") as? JsonObject
        val layers = document?.get("layers") as? JsonArray
        if (config == null || layers.isNullOrEmpty()) {
            false
        } else {
            (listOf(config) + layers).stream().allMatch { layer ->
                val item = layer as? JsonObject
                val digest = (item?.get("digest") as? JsonPrimitive)?.contentOrNull.orEmpty()
                val size = (item?.get("size") as? JsonPrimitive)?.longOrNull ?: -1
                if (!InputRule.MODEL_DIGEST.accepts(digest) || size < 0) {
                    false
                } else {
                    // Verify completeness without reading gigabytes of model weights on every status refresh.
                    val blob = root.resolve("blobs").resolve(digest.replace(':', '-'))
                    Files.isRegularFile(blob) && Files.size(blob) == size
                }
            }
        }
    } catch (exception: IOException) {
        System.err.println("Local model manifest unavailable: ${exception.javaClass.simpleName}")
        false
    } catch (exception: SerializationException) {
        System.err.println("Local model manifest invalid: ${exception.javaClass.simpleName}")
        false
    }
}
