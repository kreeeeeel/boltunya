package ru.aigram.speech

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SpeechLicenseInstallerTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `vendored notices match the upstream provenance bytes and checksums`() {
        val provenance = javaClass.getResourceAsStream("/third-party/speech/provenance.json")
        assertNotNull(provenance)
        val entries = provenance.use { Json.parseToJsonElement(it.readAllBytes().toString(Charsets.UTF_8)).jsonArray }

        entries.stream().forEach { entry ->
            val metadata = entry.jsonObject
            val name = metadata.getValue("file").jsonPrimitive.content
            val input = javaClass.getResourceAsStream("/third-party/speech/$name")
            assertNotNull(input, name)
            val bytes = input.use { it.readAllBytes() }

            assertEquals(metadata.getValue("sizeBytes").jsonPrimitive.long, bytes.size.toLong(), name)
            assertEquals(
                metadata.getValue("sha256").jsonPrimitive.content,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                name,
            )
        }
    }

    @Test
    fun `installation includes the complete versioned notices without altering their bytes`() {
        SpeechLicenseInstaller(Json).copy(directory)
        val legal = directory.resolve("legal")
        val manifest = Json.decodeFromString<SpeechLicenseManifest>(Files.readString(legal.resolve("manifest.json")))

        Files.list(legal).use { files -> assertEquals(manifest.files.size.toLong(), files.count()) }
        manifest.files.stream().forEach { name ->
            val input = javaClass.getResourceAsStream("/third-party/speech/$name")
            assertNotNull(input)
            input.use { assertContentEquals(it.readAllBytes(), Files.readAllBytes(legal.resolve(name))) }
        }
    }
}
