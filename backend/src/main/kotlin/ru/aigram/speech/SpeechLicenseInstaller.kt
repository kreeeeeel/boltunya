package ru.aigram.speech

import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import ru.aigram.exception.impl.SpeechInstallFailedException
import java.nio.file.Files
import java.nio.file.Path

private const val LICENSE_RESOURCE_DIRECTORY = "/third-party/speech/"
private const val LICENSE_MANIFEST = "manifest.json"
private const val MAX_LICENSE_FILES = 64
private const val MAX_LICENSE_FILE_BYTES = 2 * 1024 * 1024
private const val MAX_LICENSE_TOTAL_BYTES = 16 * 1024 * 1024

@Component
class SpeechLicenseInstaller(private val json: Json) {
    fun copy(target: Path) {
        val manifest = json.decodeFromString<SpeechLicenseManifest>(read(LICENSE_MANIFEST).toString(Charsets.UTF_8))
        if (manifest.files.isEmpty() || manifest.files.size > MAX_LICENSE_FILES ||
            manifest.files.stream().distinct().count() != manifest.files.size.toLong() ||
            manifest.files.stream().anyMatch { !basename(it) }
        ) {
            throw SpeechInstallFailedException()
        }
        val directory = Files.createDirectories(target.resolve("legal"))
        var totalBytes = 0
        manifest.files.stream().forEach { name ->
            val bytes = read(name)
            totalBytes += bytes.size
            if (totalBytes > MAX_LICENSE_TOTAL_BYTES) {
                throw SpeechInstallFailedException()
            }
            Files.write(directory.resolve(name), bytes)
        }
    }

    private fun read(name: String): ByteArray {
        val input = javaClass.getResourceAsStream(LICENSE_RESOURCE_DIRECTORY + name)
        if (input == null) {
            throw SpeechInstallFailedException()
        }
        return input.use {
            val bytes = it.readNBytes(MAX_LICENSE_FILE_BYTES + 1)
            if (bytes.isEmpty() || bytes.size > MAX_LICENSE_FILE_BYTES) {
                throw SpeechInstallFailedException()
            }
            bytes
        }
    }

    private fun basename(name: String): Boolean = name.isNotEmpty() && name.length <= 128 &&
        !name.contains("..") && name.chars().allMatch {
            it in 'a'.code..'z'.code || it in 'A'.code..'Z'.code || it in '0'.code..'9'.code ||
                it == '.'.code || it == '-'.code || it == '_'.code
        }
}
