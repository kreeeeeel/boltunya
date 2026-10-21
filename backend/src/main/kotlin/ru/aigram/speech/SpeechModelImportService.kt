package ru.aigram.speech

import org.springframework.stereotype.Component
import ru.aigram.domain.media.OutgoingMediaKind
import ru.aigram.domain.speech.SpeechAsset
import ru.aigram.domain.speech.SpeechAssetKind
import ru.aigram.domain.speech.SpeechAssetState
import ru.aigram.exception.AppException
import ru.aigram.exception.impl.InvalidSpeechModelImportException
import ru.aigram.media.UploadStore
import ru.aigram.resources.ResourceCatalog
import ru.aigram.telegram.TelegramGateway
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Comparator
import java.util.HexFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.stream.Stream

private const val IMPORT_FINGERPRINT_BUFFER = 65536

@Component
class SpeechModelImportService(
    directory: Path,
    private val uploads: UploadStore,
    private val archives: SpeechModelArchiveImporter,
    private val registry: SpeechCustomModelRegistry,
    private val catalog: SpeechAssetCatalog,
    private val licenses: SpeechLicenseInstaller,
    private val resources: ResourceCatalog,
    private val telegram: TelegramGateway,
) {
    private val directory = directory.resolve("speech").toAbsolutePath().normalize()

    fun importModel(
        ownerId: String,
        uploadId: String,
        title: String,
        language: String,
        license: String,
    ): SpeechAssetState {
        if (ownerId.isEmpty() || telegram.status().userId != ownerId || title.isBlank() ||
            title.length > 80 || language.length > 10 || license.length > 512 ||
            !language.chars().allMatch { it in 'a'.code..'z'.code || it in 'A'.code..'Z'.code || it == '-'.code }
        ) {
            throw InvalidSpeechModelImportException()
        }
        val leased = uploads.begin(ownerId, listOf(uploadId), OutgoingMediaKind.DOCUMENT, "").single()
        var success = false
        var staging: Path? = null
        try {
            if (!leased.path.fileName.toString().endsWith(".tar.bz2", ignoreCase = true)) {
                throw InvalidSpeechModelImportException()
            }
            Files.createDirectories(directory)
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw InvalidSpeechModelImportException()
            }
            val target = Files.createTempDirectory(directory, ".import-")
            staging = target
            val imported = archives.extract(leased.path, target)
            licenses.copy(target.resolve("runtime-notices"))
            val hash = fingerprint(leased.path)
            val languages = if (language.isEmpty()) {
                emptyList()
            } else {
                listOf(language.lowercase(Locale.ROOT))
            }
            val definition = SpeechAssetDefinition(
                SpeechAsset(
                    "custom-${UUID.randomUUID()}", title, SpeechAssetKind.SYNTHESIS, languages,
                    Files.size(leased.path),
                    license.ifEmpty { resources.text("speech/custom-model-license.txt") }, "",
                    dependencies = listOf("sherpa-runtime"), userProvided = true,
                ),
                "",
                hash,
                requiredFiles = imported.requiredFiles,
                modelFile = imported.modelFile,
            )
            Files.writeString(target.resolve("artifact.sha256"), hash)
            if (Thread.currentThread().isInterrupted) {
                throw CancellationException()
            }
            if (telegram.status().userId != ownerId) {
                throw InvalidSpeechModelImportException()
            }
            registry.publish(definition, target)
            success = true
            return catalog.state()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: AppException) {
            throw exception
        } catch (exception: Exception) {
            throw InvalidSpeechModelImportException(exception)
        } finally {
            try {
                val temporary = staging
                if (temporary != null && Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) {
                    Files.walk(temporary).use { paths ->
                        paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                    }
                }
            } finally {
                uploads.release(ownerId, listOf(uploadId), success)
            }
        }
    }

    private fun fingerprint(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        SpeechCancellableInputStream(Files.newInputStream(path)).use { input ->
            val buffer = ByteArray(IMPORT_FINGERPRINT_BUFFER)
            Stream.generate { input.read(buffer) }.takeWhile { it >= 0 }.forEach { digest.update(buffer, 0, it) }
        }
        return HexFormat.of().formatHex(digest.digest())
    }
}
