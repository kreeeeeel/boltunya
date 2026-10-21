package ru.aigram.speech

import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import ru.aigram.domain.speech.SpeechAsset
import ru.aigram.domain.speech.SpeechAssetKind
import ru.aigram.domain.speech.SpeechAssetState
import ru.aigram.domain.speech.SpeechDownload
import ru.aigram.domain.speech.SpeechDownloadPhase
import ru.aigram.exception.impl.InvalidSpeechAssetException
import ru.aigram.exception.impl.SpeechImportedModelUnavailableException
import ru.aigram.exception.impl.SpeechInstallFailedException
import ru.aigram.exception.impl.SpeechNotInstalledException
import ru.aigram.exception.impl.SpeechUnsupportedPlatformException
import ru.aigram.resources.ResourceCatalog
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.FutureTask
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Stream

private const val INSTALL_MARKER = "artifact.sha256"

@Component
class SpeechAssetCatalog(
    directory: Path,
    json: Json,
    resources: ResourceCatalog,
    private val downloader: SpeechDownloadClient,
    private val archives: SpeechArchiveInstaller,
    private val licenses: SpeechLicenseInstaller,
    private val customModels: SpeechCustomModelRegistry,
) : AutoCloseable {
    private val directory = directory.resolve("speech").toAbsolutePath().normalize()
    private val definitions = json.decodeFromString<List<SpeechAssetDefinition>>(resources.text("speech/catalog.json"))
    private val executor = ThreadPoolExecutor(
        1,
        1,
        0,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(definitions.size),
    ) { runnable ->
        Thread(runnable, "speech-installer").apply { isDaemon = true }
    }
    private val downloads = ConcurrentHashMap<String, SpeechDownload>()
    private val jobs = ConcurrentHashMap<String, FutureTask<Unit>>()
    private val tokens = ConcurrentHashMap<String, UUID>()
    private val closed = AtomicBoolean()

    fun list(): List<SpeechAsset> = allDefinitions().stream().map { it.asset.copy(installed = installed(it)) }.toList()

    fun state(): SpeechAssetState = SpeechAssetState(
        list(),
        definitions.stream().map { downloads[it.asset.id] }.flatMap<SpeechDownload> { Stream.ofNullable(it) }.toList(),
    )

    @Synchronized
    fun install(id: String): SpeechAssetState {
        if (closed.get()) {
            throw SpeechInstallFailedException()
        }
        ensurePlatform()
        val definition = definition(id)
        if (definition.asset.userProvided && !installed(definition)) {
            throw SpeechImportedModelUnavailableException()
        }
        if (installed(definition) || jobs.containsKey(id)) {
            return state()
        }
        downloads[id] = SpeechDownload(id, SpeechDownloadPhase.QUEUED, totalBytes = definition.asset.sizeBytes)
        val token = UUID.randomUUID()
        tokens[id] = token
        val job = FutureTask<Unit> { installAsset(definition, token) }
        jobs[id] = job
        executor.execute(job)
        return state()
    }

    @Synchronized
    fun cancel(id: String): SpeechAssetState {
        definition(id)
        if (downloads[id]?.phase == SpeechDownloadPhase.READY) {
            return state()
        }
        val job = jobs[id]
        if (job != null && job.cancel(true)) {
            executor.remove(job)
            downloads.computeIfPresent(id) { _, previous -> previous.copy(phase = SpeechDownloadPhase.CANCELLED) }
            jobs.remove(id, job)
            tokens.remove(id)
        }
        return state()
    }

    fun model(id: String, kind: SpeechAssetKind): SpeechAssetDefinition {
        ensurePlatform()
        val definition = definition(id)
        if (definition.asset.kind != kind) {
            throw InvalidSpeechAssetException()
        }
        if (definition.asset.userProvided && !installed(definition)) {
            throw SpeechImportedModelUnavailableException()
        }
        if (!installed(definition) || definition.asset.dependencies.stream().anyMatch { !installed(definition(it)) }) {
            throw SpeechNotInstalledException()
        }
        return definition
    }

    fun location(id: String): Path = directory.resolve(definition(id).asset.id)

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        jobs.values.stream().forEach { it.cancel(true) }
        executor.shutdownNow()
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }

    private fun definition(id: String): SpeechAssetDefinition {
        val definition = allDefinitions().stream().filter { it.asset.id == id }.findFirst().orElse(null)
        if (definition == null) {
            throw InvalidSpeechAssetException()
        }
        return definition
    }

    private fun allDefinitions(): List<SpeechAssetDefinition> = Stream.concat(
        definitions.stream(),
        customModels.list().stream(),
    ).toList()

    private fun installed(definition: SpeechAssetDefinition): Boolean {
        val target = directory.resolve(definition.asset.id)
        val marker = target.resolve(INSTALL_MARKER)
        return Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS) &&
            Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS) && Files.size(marker) <= 128 &&
            Files.readString(marker) == definition.sha256 &&
            definition.requiredFiles.stream().allMatch { Files.exists(target.resolve(it), LinkOption.NOFOLLOW_LINKS) }
    }

    private fun installAsset(definition: SpeechAssetDefinition, token: UUID) {
        val id = definition.asset.id
        val staging = directory.resolve(".$id.$token.staging")
        val archive = directory.resolve(".$id.$token.partial")
        try {
            Files.createDirectories(directory)
            delete(staging)
            update(id, token) {
                SpeechDownload(id, SpeechDownloadPhase.DOWNLOADING, totalBytes = definition.asset.sizeBytes)
            }
            downloader.download(definition, archive) { bytes ->
                update(id, token) { previous -> previous.copy(downloadedBytes = bytes) }
            }
            if (Thread.currentThread().isInterrupted) {
                throw CancellationException()
            }
            update(id, token) { previous -> previous.copy(phase = SpeechDownloadPhase.EXTRACTING) }
            archives.extract(archive, staging, definition)
            licenses.copy(staging)
            Files.writeString(staging.resolve(INSTALL_MARKER), definition.sha256)
            if (Thread.currentThread().isInterrupted) {
                throw CancellationException()
            }
            synchronized(this) {
                if (tokens[id] != token || Thread.currentThread().isInterrupted) {
                    throw CancellationException()
                }
                val destination = directory.resolve(id)
                if (Files.exists(destination)) {
                    delete(destination)
                }
                Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE)
                update(id, token) {
                    SpeechDownload(
                        id,
                        SpeechDownloadPhase.READY,
                        definition.asset.sizeBytes,
                        definition.asset.sizeBytes,
                    )
                }
            }
        } catch (exception: Exception) {
            val cancelled = exception is CancellationException || exception is InterruptedException ||
                Thread.currentThread().isInterrupted
            val phase = if (cancelled) {
                SpeechDownloadPhase.CANCELLED
            } else {
                SpeechDownloadPhase.ERROR
            }
            update(id, token) { previous ->
                val error = if (cancelled) {
                    ""
                } else {
                    "SPEECH_INSTALL_FAILED"
                }
                previous.copy(phase = phase, error = error)
            }
        } finally {
            try {
                Files.deleteIfExists(archive)
                delete(staging)
            } finally {
                synchronized(this) {
                    if (tokens.remove(id, token)) {
                        jobs.remove(id)
                    }
                }
            }
        }
    }

    @Synchronized
    private fun update(id: String, token: UUID, change: (SpeechDownload) -> SpeechDownload) {
        if (tokens[id] == token) {
            downloads.computeIfPresent(id) { _, previous -> change(previous) }
        }
    }

    private fun delete(path: Path) {
        if (Files.exists(path)) {
            Files.walk(path).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private fun ensurePlatform() {
        if (!System.getProperty("os.name").startsWith("Windows") ||
            System.getProperty("os.arch") !in setOf("amd64", "x86_64")
        ) {
            throw SpeechUnsupportedPlatformException()
        }
    }
}
