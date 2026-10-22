package ru.aigram.speech

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Test
import ru.aigram.domain.speech.SpeechAssetKind
import ru.aigram.domain.speech.SpeechEngineKind
import ru.aigram.resources.ResourceCatalog
import java.net.URI
import java.util.stream.Collectors
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpeechCatalogManifestTest {
    @Test
    fun `every curated voice matches independently reviewed release and model metadata`() {
        val definitions = speechDefinitions().stream().filter {
            it.asset.kind == SpeechAssetKind.SYNTHESIS && it.asset.engine == SpeechEngineKind.SHERPA
        }
            .collect(Collectors.toMap({ it.asset.id }, { it }))
        val proof = Json.parseToJsonElement(ResourceCatalog().text("speech/catalog-provenance.json")).jsonObject
        val voices = proof.getValue("voices").jsonArray

        assertEquals(63, definitions.size)
        assertEquals(63, voices.size)
        assertEquals(
            definitions.keys,
            voices.stream().map { it.jsonObject.getValue("id").jsonPrimitive.content }.collect(Collectors.toSet()),
        )
        assertEquals(39L, definitions.values.stream().flatMap { it.asset.languages.stream() }.distinct().count())
        voices.stream().map { it.jsonObject }.forEach { record ->
            val definition = definitions.getValue(record.getValue("id").jsonPrimitive.content)
            val archive = record.getValue("archiveName").jsonPrimitive.content
            assertEquals(record.getValue("sizeBytes").jsonPrimitive.long, definition.asset.sizeBytes)
            assertEquals(record.getValue("sha256").jsonPrimitive.content, definition.sha256)
            assertEquals(record.getValue("modelFile").jsonPrimitive.content, definition.modelFile)
            assertEquals(archive.removeSuffix(".tar.bz2"), definition.archiveRoot)
            assertEquals(
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$archive",
                definition.downloadUrl,
            )
            assertEquals(listOf(definition.modelFile, "tokens.txt", "espeak-ng-data"), definition.requiredFiles)
            assertEquals(listOf("sherpa-runtime"), definition.asset.dependencies)
            assertEquals(listOf(record.getValue("language").jsonPrimitive.content), definition.asset.languages)
            assertEquals(1L, record.getValue("speakers").jsonPrimitive.long)
            assertEquals("espeak", record.getValue("phonemizer").jsonPrimitive.content)
            assertTrue(record.getValue("assetId").jsonPrimitive.long > 0)
            assertTrue(record.getValue("sampleRate").jsonPrimitive.long in 16000L..48000L)
            assertTrue(definition.asset.license.isNotBlank())
            assertFalse(definition.asset.userProvided)
            assertEquals("https", URI(definition.asset.sourceUrl).scheme)
            assertEquals(64, definition.sha256.length)
            assertTrue(definition.sha256.chars().allMatch { it in '0'.code..'9'.code || it in 'a'.code..'f'.code })
        }
    }

    @Test
    fun `quality variants are not presented as extra speakers and legacy model identifiers remain available`() {
        val definitions = speechDefinitions()
        val synthesis = definitions.stream().filter { it.asset.kind == SpeechAssetKind.SYNTHESIS }.toList()
        val speakerKeys = synthesis.stream().map {
            val key = it.modelFile.removeSuffix(".onnx")
            val quality = key.substringAfterLast('-')
            if (quality in setOf("medium", "low", "x_low", "high")) {
                key.substringBeforeLast('-')
            } else {
                key
            }
        }.toList()

        assertEquals(synthesis.size.toLong(), speakerKeys.stream().distinct().count())
        assertTrue(
            definitions.stream().map { it.asset.id }.toList().containsAll(
                listOf("sherpa-runtime", "whisper-tiny", "piper-ru-irina", "piper-ru-ruslan", "piper-en-amy"),
            ),
        )
        assertEquals(
            setOf("piper-en-glados", "piper-de-glados", "piper-de-glados-turret", "piper-es-glados"),
            synthesis.stream().filter { it.asset.id.contains("glados") }.map { it.asset.id }
                .collect(Collectors.toSet()),
        )
    }

    @Test
    fun `character models retain pinned source names hashes and explicit conversion dependencies`() {
        val definitions = speechDefinitions()
        val proof = Json.parseToJsonElement(ResourceCatalog().text("speech/catalog-provenance.json")).jsonObject
        val voices = proof.getValue("conversionVoices").jsonArray
        val conversion = definitions.filter {
            it.asset.kind == SpeechAssetKind.SYNTHESIS && it.asset.engine == SpeechEngineKind.APPLIO_RVC
        }

        assertEquals(15, conversion.size)
        assertEquals(15, voices.size)
        voices.stream().map { it.jsonObject }.forEach { record ->
            val model = conversion.single { it.asset.id == record.getValue("id").jsonPrimitive.content }
            assertEquals(record.getValue("sha256").jsonPrimitive.content, model.sha256)
            assertEquals(record.getValue("sizeBytes").jsonPrimitive.long, model.asset.sizeBytes)
            assertEquals(record.getValue("modelFile").jsonPrimitive.content, model.modelFile)
            assertEquals(record.getValue("indexFile").jsonPrimitive.content, model.indexFile)
            assertTrue(model.downloadUrl.contains(record.getValue("revision").jsonPrimitive.content))
            assertEquals(listOf(model.modelFile, model.indexFile), model.requiredFiles)
            assertTrue(
                model.asset.dependencies.containsAll(listOf("sherpa-runtime", "applio-runtime", model.baseModelId)),
            )
            assertTrue(model.asset.license.contains("OpenRAIL"))
            assertTrue(model.asset.languages.all { it == "ru" || it == "en" })
        }
        val runtime = definitions.single { it.asset.id == "applio-runtime" }
        val runtimeProof = proof.getValue("conversionRuntime").jsonObject
        assertEquals(6775972339L, runtime.asset.sizeBytes)
        assertEquals(runtimeProof.getValue("sha256").jsonPrimitive.content, runtime.sha256)
        assertTrue(runtimeProof.getValue("expandedBytes").jsonPrimitive.long < 12L * 1024 * 1024 * 1024)
        assertTrue(runtimeProof.getValue("entries").jsonPrimitive.long < 50000)
        assertTrue(runtime.requiredFiles.containsAll(listOf("env/python.exe", "core.py")))
    }
}
