package ru.aigram.contract.validation

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

class ContractSource(directory: Path) {
    private val root = directory.toAbsolutePath().normalize()
    private val yaml = Yaml(SafeConstructor(LoaderOptions().apply { setAllowDuplicateKeys(false) }))
    private val documents = mutableMapOf<Path, JsonElement>()

    fun load(): JsonObject = expand(read(root.resolve("api.yaml")), root.resolve("api.yaml"), emptySet()).jsonObject

    fun raw(): JsonObject = read(root.resolve("api.yaml")).jsonObject

    fun sourceDocuments(): Map<Path, JsonObject> {
        load()
        return documents.entries.stream().collect(Collectors.toMap({ it.key }, { it.value.jsonObject }))
    }

    private fun read(path: Path): JsonElement = documents.getOrPut(path) {
        Files.newBufferedReader(path).use { reader -> toJson(yaml.load<Any?>(reader)) }
    }

    private fun expand(value: JsonElement, source: Path, active: Set<String>): JsonElement = when (value) {
        is JsonArray -> JsonArray(value.stream().map { expand(it, source, active) }.toList())
        is JsonObject -> {
            val reference = value["\$ref"]
            if (reference == null) {
                JsonObject(
                    value.entries.stream().collect(
                        Collectors.toMap({
                            it.key
                        }, { expand(it.value, source, active) }),
                    ),
                )
            } else {
                val uri = URI(reference.jsonPrimitive.content)
                if (uri.isAbsolute || uri.authority != null || uri.query != null) {
                    throw IllegalArgumentException("Only local relative contract references are allowed: $uri")
                }
                val target = if (uri.path.isNullOrEmpty()) {
                    source
                } else {
                    source.parent.resolve(uri.path).normalize()
                }
                if (!target.startsWith(root)) {
                    throw IllegalArgumentException("Contract reference escapes the schema directory: $uri")
                }
                val identity = "$target#${uri.fragment.orEmpty()}"
                if (identity in active) {
                    throw IllegalArgumentException("Unexpected recursive reference in the migration baseline: $uri")
                }
                val resolved = expand(pointer(read(target), uri.fragment.orEmpty()), target, active + identity)
                val siblings = value.entries.stream().filter { it.key != "\$ref" }
                    .collect(Collectors.toMap({ it.key }, { expand(it.value, source, active) }))
                if (siblings.isEmpty()) {
                    resolved
                } else {
                    JsonObject(resolved.jsonObject + siblings)
                }
            }
        }
        else -> value
    }

    private fun pointer(document: JsonElement, fragment: String): JsonElement {
        if (fragment.isEmpty()) {
            return document
        }
        if (!fragment.startsWith('/')) {
            throw IllegalArgumentException("Unsupported contract JSON pointer: $fragment")
        }
        return fragment.substring(1).split('/').stream().sequential().reduce(
            document,
            { current, segment ->
                val key = segment.replace("~1", "/").replace("~0", "~")
                val selected = when (current) {
                    is JsonObject -> current[key]
                    is JsonArray -> key.toIntOrNull()?.let(current::getOrNull)
                    else -> null
                }
                if (selected == null) {
                    throw IllegalArgumentException("Unresolved contract JSON pointer: $fragment")
                }
                selected
            },
            { _, _ -> throw IllegalStateException("JSON pointer traversal must remain sequential") },
        )
    }

    private fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is List<*> -> JsonArray(value.stream().map(::toJson).toList())
        is Map<*, *> -> JsonObject(
            value.entries.stream().collect(Collectors.toMap({ it.key.toString() }, { toJson(it.value) })),
        )
        else -> throw IllegalArgumentException("Unsupported YAML scalar: ${value.javaClass.simpleName}")
    }
}
