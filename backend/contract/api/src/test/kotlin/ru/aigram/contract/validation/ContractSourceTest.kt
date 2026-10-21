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
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val FEATURE_TAGS =
    setOf("chats", "telegram", "media", "memory", "activity", "models", "settings", "system", "speech")
private val SPEECH_MODELS = setOf(
    "VoiceReplyMode", "DraftOutput", "GeneratedVoice", "SpeechAsset", "SpeechAssetKind",
    "SpeechDownload", "SpeechDownloadPhase", "SpeechState", "VoiceDraftRequest", "VoiceSendRequest",
    "SpeechPreviewRequest", "SpeechModelImportRequest",
)
private val SPEECH_PATHS = setOf(
    "/api/speech",
    "/api/speech/preview",
    "/api/speech/models/import",
    "/api/speech/assets/{assetId}/install",
    "/api/speech/drafts",
    "/api/speech/audio/{voiceId}",
    "/api/chats/{chatId}/voice",
)
private val SPEECH_OPERATIONS = setOf(
    "GET /api/speech",
    "POST /api/speech/preview",
    "POST /api/speech/models/import",
    "POST /api/speech/assets/{assetId}/install",
    "DELETE /api/speech/assets/{assetId}/install",
    "POST /api/speech/drafts",
    "GET /api/speech/audio/{voiceId}",
    "POST /api/chats/{chatId}/voice",
)
private val REQUEST_MODELS = setOf(
    "ChatRuleInput",
    "ModelProfileOptionsInput",
    "ModelCapabilitiesInput",
    "ModelPromptSettingsInput",
    "AppearanceSettingsInput",
    "ActivityHistorySettingsInput",
)
private val MESSAGE_EDIT_MODELS = setOf(
    "MessageEditKind",
    "MessageEditEntityKind",
    "MessageEditEntity",
    "MessageEditOptions",
    "MessageEditRequest",
)
private const val MESSAGE_EDIT_PATH = "/api/chats/{chatId}/messages/{messageId}/edit"
private val IDLE_MESSAGE_MODELS = setOf("ProactiveMessageMode", "ReplyInterruptionMode", "ReplyProcessingMode")

class ContractSourceTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `all relative sources resolve and preserve every operation model and root wire constraint`() {
        val document = ContractSource(Path.of("schema")).load()
        val expected = fixture("wire-baseline.json")
        val corrections = fixture("spring-contract-corrections.json")
        val additions = corrections.getValue("additions").jsonObject
        val extras = fixture("extracted-models.json")
        val audit = ContractWireAudit(document)
        val actual = audit.snapshot(
            expected.getValue("schemas").jsonObject.keys + REQUEST_MODELS + MESSAGE_EDIT_MODELS + IDLE_MESSAGE_MODELS +
                SPEECH_MODELS,
        )
        assertEquals(expected.getValue("root"), actual.getValue("root"), "Root metadata or session security changed")
        assertEquals(
            setOf("/api/engine/restart", MESSAGE_EDIT_PATH) + SPEECH_PATHS,
            additions.getValue("paths").jsonObject.keys,
        )
        assertEquals(
            setOf("POST /api/engine/restart", "GET $MESSAGE_EDIT_PATH", "PUT $MESSAGE_EDIT_PATH") + SPEECH_OPERATIONS,
            additions.getValue("operations").jsonObject.keys,
        )
        assertEquals(
            REQUEST_MODELS + MESSAGE_EDIT_MODELS + IDLE_MESSAGE_MODELS + SPEECH_MODELS,
            additions.getValue("schemas").jsonObject.keys,
        )
        listOf("paths", "operations", "schemas").stream().forEach { group ->
            val old = expected.getValue(group).jsonObject
            val current = actual.getValue(group).jsonObject
            val reviewed = corrections.getValue(group).jsonObject
            val added = additions.getValue(group).jsonObject
            assertTrue(old.keys.containsAll(reviewed.keys), "$group corrections must name original baseline entries")
            assertTrue(old.keys.intersect(added.keys).isEmpty(), "$group additions must be new entries")
            assertTrue(current.keys.containsAll(old.keys), "Original $group names must never disappear")
            assertEquals(old.keys + added.keys, current.keys, "Unreviewed $group names changed")
            current.entries.stream().forEach { (name, hash) ->
                val corrected = added[name] ?: reviewed[name] ?: old.getValue(name)
                assertEquals(corrected, hash, "Wire semantics changed for $group: $name")
            }
        }
        assertEquals(68, document.getValue("paths").jsonObject.size)
        assertEquals(84, audit.operations().size)
        assertEquals(
            expected.getValue("schemas").jsonObject.keys + extras.keys + REQUEST_MODELS + MESSAGE_EDIT_MODELS +
                IDLE_MESSAGE_MODELS + SPEECH_MODELS,
            document.getValue("components").jsonObject.getValue("schemas").jsonObject.keys,
        )
        assertEquals(134, document.getValue("components").jsonObject.getValue("schemas").jsonObject.size)
        assertFalse(Files.exists(Path.of("../api.yaml")), "The old duplicate contract must not remain")
        Files.walk(Path.of("schema")).use { files ->
            assertEquals(144L, files.filter { Files.isRegularFile(it) && it.toString().endsWith(".yaml") }.count())
        }
    }

    @Test
    fun `optional reply entities formatting and literal idle messages retain safe defaults`() {
        val schemas = ContractSource(Path.of("schema")).load().getValue("components").jsonObject
            .getValue("schemas").jsonObject
        val settings = schemas.getValue("Settings").jsonObject.getValue("properties").jsonObject
        val update = schemas.getValue("SettingsUpdate").jsonObject.getValue("properties").jsonObject
        assertEquals(JsonPrimitive(false), settings.getValue("formatReplies").jsonObject["default"])
        assertFalse(update.getValue("formatReplies").jsonObject.containsKey("default"))
        assertFalse(update.getValue("replyProcessingMode").jsonObject.containsKey("default"))
        listOf("Reply" to "entities", "Text" to "entities", "SendMediaRequest" to "captionEntities")
            .stream().forEach { (name, property) ->
                val model = schemas.getValue(name).jsonObject
                val entity = model.getValue("properties").jsonObject.getValue(property).jsonObject
                assertEquals(JsonPrimitive(100), entity["maxItems"])
                assertFalse(model["required"]?.jsonArray?.contains(JsonPrimitive(property)) == true)
            }
        val rule = schemas.getValue("ChatRuleInput").jsonObject.getValue("properties").jsonObject
        assertEquals(JsonPrimitive("ai"), rule.getValue("proactiveMessageMode").jsonObject["default"])
        assertEquals(JsonPrimitive(4096), rule.getValue("proactiveText").jsonObject["maxLength"])
        assertEquals(
            JsonArray(listOf(JsonPrimitive("ai"), JsonPrimitive("text"))),
            schemas.getValue("ProactiveMessageMode").jsonObject["enum"],
        )
    }

    @Test
    fun `message edits require a revision and preserve opaque entities without a markdown round trip`() {
        val document = ContractSource(Path.of("schema")).load()
        val schemas = document.getValue("components").jsonObject.getValue("schemas").jsonObject
        val operation = document.getValue("paths").jsonObject.getValue(MESSAGE_EDIT_PATH).jsonObject
        assertEquals("messageEditOptions", operation.getValue("get").jsonObject["operationId"]?.jsonPrimitive?.content)
        assertEquals("editMessage", operation.getValue("put").jsonObject["operationId"]?.jsonPrimitive?.content)

        val request = schemas.getValue("MessageEditRequest").jsonObject
        assertEquals(
            setOf("text", "entities", "revision"),
            request.getValue("required").jsonArray.stream().map { it.jsonPrimitive.content }.toList().toSet(),
        )
        val properties = request.getValue("properties").jsonObject
        val revision = properties.getValue("revision").jsonObject
        assertEquals(JsonPrimitive(64), revision["minLength"])
        assertEquals(JsonPrimitive(64), revision["maxLength"])
        assertFalse(properties.getValue("text").jsonObject.containsKey("minLength"), "Empty captions must remain valid")

        val entity = schemas.getValue("MessageEditEntity").jsonObject.getValue("properties").jsonObject
        assertEquals(JsonPrimitive(0), entity.getValue("offset").jsonObject["minimum"])
        assertEquals(JsonPrimitive(1), entity.getValue("length").jsonObject["minimum"])
        assertEquals(JsonPrimitive(true), entity.getValue("sourceIndex").jsonObject["nullable"])
        assertEquals(JsonPrimitive(true), entity.getValue("typeHint").jsonObject["readOnly"])
        val kinds = schemas.getValue("MessageEditEntityKind").jsonObject.getValue("enum").jsonArray
        assertTrue(kinds.contains(JsonPrimitive("existing")), "Unknown Telegram entity types must not be lost")
        assertTrue(
            kinds.containsAll(listOf(JsonPrimitive("texturl"), JsonPrimitive("spoiler"), JsonPrimitive("quote"))),
        )

        val message = schemas.getValue("Message").jsonObject
        assertFalse(message.getValue("required").jsonArray.contains(JsonPrimitive("editedAt")))
        val edited = message.getValue("properties").jsonObject.getValue("editedAt").jsonObject
        assertEquals(JsonPrimitive("int64"), edited["format"])
        assertEquals(JsonPrimitive(0), edited["default"])
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
        assertEquals(
            "restartEngine",
            operations.getValue("POST /api/engine/restart").getValue("operationId").jsonPrimitive.content,
        )
        val tags = operations.values.stream().map { it.getValue("tags").jsonArray }.toList()
        assertTrue(tags.stream().allMatch { it.size == 1 && it.single().jsonPrimitive.content in FEATURE_TAGS })
        assertEquals(FEATURE_TAGS, tags.stream().map { it.single().jsonPrimitive.content }.collect(Collectors.toSet()))
    }

    @Test
    fun `defaulted request models do not weaken complete response requirements`() {
        val schemas = ContractSource(Path.of("schema")).load().getValue("components").jsonObject
            .getValue("schemas").jsonObject
        val chat = schemas.getValue("ChatRule").jsonObject
        val input = schemas.getValue("ChatRuleInput").jsonObject

        assertEquals(
            setOf(
                "enabled", "style", "model", "instructions", "contextMessages", "maxTokens", "delaySeconds",
                "creativity", "emoji", "modelProfileId", "deliveryMode", "analyzePhotos", "aiStickers",
            ),
            chat.getValue("required").jsonArray.stream().map { it.jsonPrimitive.content }.collect(Collectors.toSet()),
        )
        assertFalse(input.containsKey("required"))
        assertEquals(chat.getValue("properties").jsonObject.keys, input.getValue("properties").jsonObject.keys)
        assertEquals(
            JsonPrimitive(false),
            input.getValue("properties").jsonObject.getValue("enabled").jsonObject.getValue("default"),
        )
        listOf(
            "ModelProfileOptions",
            "ModelCapabilities",
            "AppearanceSettings",
            "ActivityHistorySettings",
        ).stream().forEach { name ->
            assertTrue(schemas.getValue(name).jsonObject.getValue("required").jsonArray.isNotEmpty(), name)
            assertFalse(schemas.getValue("${name}Input").jsonObject.containsKey("required"), name)
        }
        assertEquals(
            JsonArray(listOf(JsonPrimitive("model"))),
            schemas.getValue("ModelPromptSettingsInput").jsonObject.getValue("required"),
        )
        assertEquals(
            JsonPrimitive(true),
            schemas.getValue("ModelProfileUpdate").jsonObject.getValue("properties").jsonObject
                .getValue("apiKey").jsonObject.getValue("nullable"),
        )
    }

    @Test
    fun `Kotlin object default marker is restricted to explicitly empty nonnullable input defaults`() {
        val schemas = ContractSource(Path.of("schema")).load().getValue("components").jsonObject
            .getValue("schemas").jsonObject
        val markers = schemas.entries.stream().flatMap { (name, model) ->
            model.jsonObject["properties"]?.jsonObject?.entries?.stream()
                ?.filter { it.value.jsonObject["x-kotlin-empty-default"] == JsonPrimitive(true) }
                ?.map { "$name.${it.key}" to it.value.jsonObject } ?: Stream.empty()
        }.collect(Collectors.toMap({ it.first }, { it.second }))

        assertEquals(
            setOf("ModelProfileUpdate.options", "ModelProfileUpdate.capabilities", "AppearanceSettingsInput.tokens"),
            markers.keys,
        )
        markers.entries.stream().forEach { (name, property) ->
            assertEquals(JsonObject(emptyMap()), property.getValue("default"), name)
            assertFalse(property["nullable"] == JsonPrimitive(true), name)
        }
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
        assertEquals(setOf("application/octet-stream", "image/jpeg", "image/png", "video/mp4", "*/*"), body.keys)
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
                Stream.of(child.jsonPrimitive.content)
            } else {
                references(child).stream()
            }
        }.toList()
        is JsonArray -> value.stream().flatMap { references(it).stream() }.toList()
        else -> emptyList()
    }
}
