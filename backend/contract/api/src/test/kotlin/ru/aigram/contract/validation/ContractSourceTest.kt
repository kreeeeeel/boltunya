package ru.aigram.contract.validation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val FEATURE_TAGS = setOf("chats", "telegram", "media", "memory", "activity", "models", "settings", "system")

class ContractSourceTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `all relative sources resolve and preserve every operation model and root wire constraint`() {
        val document = ContractSource(Path.of("schema")).load()
        val expected = fixture("wire-baseline.json")
        val extras = fixture("extracted-models.json")
        val audit = ContractWireAudit(document)
        val actual = audit.snapshot(expected.getValue("schemas").jsonObject.keys)
        assertEquals(expected.getValue("root"), actual.getValue("root"), "Root metadata or session security changed")
        listOf("paths", "operations", "schemas").stream().forEach { group ->
            val old = expected.getValue(group).jsonObject
            val current = actual.getValue(group).jsonObject
            assertEquals(old.keys, current.keys, "$group names changed")
            old.entries.stream().forEach { (name, hash) ->
                assertEquals(hash, current[name], "Wire semantics changed for $group: $name")
            }
        }
        assertEquals(59, document.getValue("paths").jsonObject.size)
        assertEquals(73, audit.operations().size)
        assertEquals(
            expected.getValue("schemas").jsonObject.keys + extras.keys,
            document.getValue("components").jsonObject.getValue("schemas").jsonObject.keys,
        )
        assertEquals(108, document.getValue("components").jsonObject.getValue("schemas").jsonObject.size)
        assertFalse(Files.exists(Path.of("../api.yaml")), "The old duplicate contract must not remain")
        Files.walk(Path.of("schema")).use { files ->
            assertEquals(117L, files.filter { Files.isRegularFile(it) && it.toString().endsWith(".yaml") }.count())
        }
    }

    @Test
    fun `operations have stable unique ids and one of the agreed feature tags`() {
        val operations = ContractWireAudit(ContractSource(Path.of("schema")).load()).operations()
        val identifiers = operations.values.stream().map { it.getValue("operationId").jsonPrimitive.content }.toList()
        assertTrue(identifiers.stream().allMatch { it.isNotBlank() })
        assertEquals(identifiers.size.toLong(), identifiers.stream().distinct().count())
        assertEquals(
            "getModelPrompts",
            operations.getValue("GET /api/model-prompts").getValue("operationId").jsonPrimitive.content,
        )
        assertEquals(
            "saveModelPrompts",
            operations.getValue("PUT /api/model-prompts").getValue("operationId").jsonPrimitive.content,
        )
        assertEquals(
            "estimatePromptBudget",
            operations.getValue("POST /api/prompts/budget").getValue("operationId").jsonPrimitive.content,
        )
        val tags = operations.values.stream().map { it.getValue("tags").jsonArray }.toList()
        assertTrue(tags.stream().allMatch { it.size == 1 && it.single().jsonPrimitive.content in FEATURE_TAGS })
        assertEquals(FEATURE_TAGS, tags.stream().map { it.single().jsonPrimitive.content }.collect(Collectors.toSet()))
    }

    @Test
    fun `preserves PATCH optionality nullable sampling binary upload and session security`() {
        val document = ContractSource(Path.of("schema")).load()
        val schemas = document.getValue("components").jsonObject.getValue("schemas").jsonObject
        val update = schemas.getValue("SettingsUpdate").jsonObject
        assertFalse(update.containsKey("required"))
        assertFalse(
            update.getValue(
                "properties",
            ).jsonObject.getValue("defaultModelProfileId").jsonObject.containsKey("default"),
        )
        val options = schemas.getValue("ModelProfileOptions").jsonObject
        val temperature = options.getValue("properties").jsonObject.getValue("temperature").jsonObject
        assertEquals(JsonPrimitive(true), temperature.getValue("nullable"))
        assertEquals(JsonPrimitive(0), temperature.getValue("minimum"))
        assertEquals(JsonPrimitive(2), temperature.getValue("maximum"))
        assertFalse(options.getValue("required").jsonArray.contains(JsonPrimitive("temperature")))
        val upload = document.getValue(
            "paths",
        ).jsonObject.getValue("/api/uploads").jsonObject.getValue("post").jsonObject
        assertEquals(JsonPrimitive(true), upload.getValue("requestBody").jsonObject.getValue("required"))
        val body = upload.getValue("requestBody").jsonObject.getValue("content").jsonObject
        assertEquals(setOf("application/octet-stream", "image/jpeg", "image/png", "video/mp4"), body.keys)
        assertTrue(
            body.values.stream().allMatch {
                it.jsonObject.getValue("schema").jsonObject.getValue("format") ==
                    JsonPrimitive("binary")
            },
        )
        val security = document.getValue("components").jsonObject.getValue("securitySchemes").jsonObject
        assertEquals(JsonPrimitive("tiho"), security.getValue("sessionCookie").jsonObject.getValue("name"))
        assertEquals(
            JsonArray(listOf(JsonObject(mapOf("sessionCookie" to JsonArray(emptyList()))))),
            document.getValue("security"),
        )
    }

    @Test
    fun `broken references remote references and directory escapes fail instead of being ignored`() {
        listOf(
            "./missing.yaml#/Missing",
            "https://example.invalid/schema.yaml#/Remote",
            "../outside.yaml#/Outside",
        ).stream()
            .forEach { reference ->
                Files.writeString(directory.resolve("api.yaml"), "value:\n    \$ref: '$reference'\n")
                assertFailsWith<Exception>(reference) { ContractSource(directory).load() }
            }
    }

    @Test
    fun `enums and JSON request bodies are named without duplicating identical enum schemas`() {
        val source = ContractSource(Path.of("schema"))
        val document = source.load()
        val extras = fixture("extracted-models.json")
        val schemas = document.getValue("components").jsonObject.getValue("schemas").jsonObject
        assertEquals(
            28L,
            extras.values.stream().filter {
                it.jsonObject.getValue("kind") == JsonPrimitive("enum")
            }.count(),
        )
        assertEquals(
            2L,
            extras.values.stream().filter {
                it.jsonObject.getValue("kind") == JsonPrimitive("request")
            }.count(),
        )
        assertEquals(
            34,
            extras.values.stream().filter { it.jsonObject.getValue("kind") == JsonPrimitive("enum") }
                .mapToInt { it.jsonObject.getValue("origins").jsonArray.size }.sum(),
        )
        val enumHashes = extras.entries.stream().filter {
            it.value.jsonObject.getValue("kind") == JsonPrimitive("enum")
        }
            .map { WireFingerprint().digest(schemas.getValue(it.key)) }.toList()
        assertEquals(enumHashes.size.toLong(), enumHashes.stream().distinct().count(), "Identical enums must be shared")
        source.sourceDocuments().entries.stream().filter {
            it.key.startsWith(Path.of("schema/models").toAbsolutePath())
        }
            .forEach { (file, values) ->
                assertEquals(1, values.size, "Each model belongs in its own file: $file")
                assertEquals(0, inlineEnums(values.values.single(), topLevel = true), "Inline enum remains: $file")
            }
        extras.entries.stream().forEach { (name, metadata) ->
            val schema = schemas.getValue(name).jsonObject
            if (metadata.jsonObject.getValue("kind") == JsonPrimitive("enum")) {
                assertTrue(schema.getValue("enum").jsonArray.isNotEmpty(), name)
            } else {
                assertEquals(JsonPrimitive("object"), schema.getValue("type"), name)
            }
        }
    }

    @Test
    fun `the single generator bundle has only internal model refs and the same complete wire semantics`() {
        val canonical = ContractSource(Path.of("schema")).load()
        val bundledSource = ContractSource(Path.of("build/bundled"))
        val bundled = bundledSource.load()
        assertEquals(ContractWireAudit(canonical).snapshot(), ContractWireAudit(bundled).snapshot())
        val references = references(bundledSource.raw())
        assertTrue(references.isNotEmpty())
        assertTrue(references.stream().allMatch { it.startsWith("#/components/schemas/") })
        val engineModels = bundledSource.raw().getValue("components").jsonObject.getValue("schemas").jsonObject
            .getValue("Engine").jsonObject.getValue("properties").jsonObject.getValue("models").jsonObject
            .getValue("items").jsonObject
        assertEquals(JsonPrimitive("#/components/schemas/Model"), engineModels.getValue("\$ref"))
    }

    private fun fixture(name: String): JsonObject {
        val resource = javaClass.getResourceAsStream("/$name")
        if (resource == null) {
            throw IllegalStateException("Missing contract compatibility fixture: $name")
        }
        return resource.use { Json.parseToJsonElement(String(it.readAllBytes(), Charsets.UTF_8)).jsonObject }
    }

    private fun inlineEnums(value: JsonElement, topLevel: Boolean): Int = when (value) {
        is JsonObject -> {
            val own = if (!topLevel && value.containsKey("enum")) {
                1
            } else {
                0
            }
            own + value.values.stream().mapToInt { inlineEnums(it, false) }.sum()
        }
        is JsonArray -> value.stream().mapToInt { inlineEnums(it, false) }.sum()
        else -> 0
    }

    private fun references(value: JsonElement): List<String> = when (value) {
        is JsonObject -> value.entries.stream().flatMap { (name, child) ->
            if (name == "\$ref") {
                java.util.stream.Stream.of(child.jsonPrimitive.content)
            } else {
                references(child).stream()
            }
        }.toList()
        is JsonArray -> value.stream().flatMap { references(it).stream() }.toList()
        else -> emptyList()
    }
}
