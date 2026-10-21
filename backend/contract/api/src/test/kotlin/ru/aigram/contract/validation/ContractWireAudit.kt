package ru.aigram.contract.validation

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.stream.Collectors

private val HTTP_METHODS = setOf("get", "post", "put", "patch", "delete", "head", "options")
private val ADDED_OPERATION_IDS = setOf("GET /api/model-prompts", "PUT /api/model-prompts", "POST /api/prompts/budget")

class ContractWireAudit(private val document: JsonObject) {
    private val fingerprint = WireFingerprint()
    private val paths = document.getValue("paths").jsonObject
    private val components = document.getValue("components").jsonObject

    fun operations(): Map<String, JsonObject> = paths.entries.stream().flatMap { (path, item) ->
        item.jsonObject.entries.stream().filter { it.key in HTTP_METHODS }
            .map { (method, operation) -> "${method.uppercase()} $path" to operation.jsonObject }
    }.collect(Collectors.toMap({ it.first }, { it.second }))

    fun snapshot(originalSchemaNames: Set<String>? = null): JsonObject {
        val pathHashes = paths.entries.stream().collect(
            Collectors.toMap({ it.key }, { fingerprint.digest(without(it.value.jsonObject, HTTP_METHODS)) }),
        )
        val operationHashes = operations().entries.stream().collect(
            Collectors.toMap(
                { it.key },
                {
                    val excluded = if (it.key in ADDED_OPERATION_IDS) {
                        setOf("tags", "operationId")
                    } else {
                        setOf("tags")
                    }
                    fingerprint.digest(without(it.value, excluded))
                },
            ),
        )
        val schemaHashes = components.getValue("schemas").jsonObject.entries.stream()
            .filter { originalSchemaNames == null || it.key in originalSchemaNames }
            .collect(Collectors.toMap({ it.key }, { fingerprint.digest(it.value) }))
        val metadata = without(document, setOf("paths", "components")) +
            ("components" to without(components, setOf("schemas")))
        return JsonObject(
            mapOf(
                "root" to fingerprint.digest(JsonObject(metadata)),
                "paths" to JsonObject(pathHashes),
                "operations" to JsonObject(operationHashes),
                "schemas" to JsonObject(schemaHashes),
            ),
        )
    }

    private fun without(value: JsonObject, excluded: Set<String>): JsonObject = JsonObject(
        value.entries.stream().filter { it.key !in excluded }
            .collect(Collectors.toMap({ it.key }, { it.value })),
    )
}
