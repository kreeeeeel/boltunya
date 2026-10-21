package ru.aigram.speech

import kotlinx.serialization.json.Json
import ru.aigram.domain.speech.SpeechDownloadPhase
import ru.aigram.resources.ResourceCatalog
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

fun verifiedSpeechCatalog(directory: Path, verified: Path, assets: List<String>): SpeechAssetCatalog {
    val downloader = SpeechArtifactDownloader()
    val failure = AtomicReference<Exception>()
    val local = SpeechDownloadClient { definition, target, progress ->
        val archive = verified.resolve(URI(definition.downloadUrl).path.substringAfterLast('/'))
        try {
            Files.newInputStream(archive).use { input -> downloader.copy(definition, input, target, progress) }
        } catch (exception: Exception) {
            failure.compareAndSet(null, exception)
            throw exception
        }
    }
    val catalog = SpeechAssetCatalog(
        directory,
        Json,
        ResourceCatalog(),
        local,
        SpeechVerificationArchiveInstaller(failure),
        SpeechVerificationLicenseInstaller(Json, failure),
        SpeechCustomModelRegistry(directory, Json),
    )
    var installed = false
    try {
        assets.stream().forEach { id ->
            catalog.install(id)
            awaitSpeech(120) {
                val download = catalog.state().downloads.stream().filter { it.assetId == id }.findFirst().orElse(null)
                if (download != null &&
                    (download.phase == SpeechDownloadPhase.ERROR || download.phase == SpeechDownloadPhase.CANCELLED)
                ) {
                    throw AssertionError(
                        "Speech asset $id installation did not complete: ${download.phase}",
                        failure.get(),
                    )
                }
                download?.phase == SpeechDownloadPhase.READY
            }
        }
        installed = true
        return catalog
    } finally {
        if (!installed) {
            catalog.close()
        }
    }
}
