package ru.aigram.contract.validation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpeechContractTest {
    @Test
    fun `speech stays opt in and transport exposes no filesystem path or arbitrary download URL`() {
        val source = ContractSource(Path.of("schema")).load()
        val schemas = source.getValue("components").jsonObject.getValue("schemas").jsonObject
        val settings = schemas.getValue("Settings").jsonObject.getValue("properties").jsonObject
        val patch = schemas.getValue("SettingsUpdate").jsonObject.getValue("properties").jsonObject
        assertEquals(JsonPrimitive(false), settings.getValue("voiceInputEnabled").jsonObject["default"])
        assertFalse(patch.getValue("voiceInputEnabled").jsonObject.containsKey("default"))
        assertFalse(patch.getValue("voiceReplyMode").jsonObject.containsKey("default"))
        assertEquals(JsonPrimitive(0), patch.getValue("voiceReplyProbability").jsonObject["minimum"])
        assertEquals(JsonPrimitive(100), patch.getValue("voiceReplyProbability").jsonObject["maximum"])

        listOf(
            "GeneratedVoice",
            "VoiceSendRequest",
            "VoiceDraftRequest",
            "SpeechPreviewRequest",
            "SpeechModelImportRequest",
        )
            .stream().forEach { name ->
                val properties = schemas.getValue(name).jsonObject.getValue("properties").jsonObject
                assertFalse(properties.containsKey("path"), name)
                assertFalse(properties.containsKey("downloadUrl"), name)
            }
        val operations = ContractWireAudit(source).operations()
        assertTrue(operations.values.stream().anyMatch { it["operationId"] == JsonPrimitive("enqueueVoiceDraft") })
        val report = Path.of("build/reports/speech-wire-snapshot.json")
        Files.createDirectories(report.parent)
        Files.writeString(report, Json.encodeToString(ContractWireAudit(source).snapshot()))
    }
}
