package ru.boltunya.ai

import jakarta.inject.Inject
import ru.boltunya.domain.EngineStatus
import ru.boltunya.domain.ModelInfo
import ru.boltunya.exception.IntegrationException
import ru.boltunya.exception.InvalidInputException
import ru.boltunya.exception.impl.DownloadAlreadyRunningException
import ru.boltunya.exception.impl.EngineDownloadFailedException
import ru.boltunya.exception.impl.EngineInstallFailedException
import ru.boltunya.exception.impl.EngineWarmupFailedException
import ru.boltunya.exception.impl.InvalidEngineArchiveException
import ru.boltunya.exception.impl.ModelPullInterruptedException
import ru.boltunya.exception.impl.RegistryRedirectException
import ru.boltunya.exception.impl.UnsupportedModelException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

private const val OLLAMA_VERSION = "0.40.4"

class EngineManager @Inject constructor(
    private val directory: Path,
    private val ollama: OllamaClient,
    private val models: ModelCatalog = ModelCatalog(),
    private val installer: RegistryModelInstaller = RegistryModelInstaller(),
    private val downloadWriter: DownloadWriter = DownloadWriter(),
    private val processes: EngineProcessLocator = EngineProcessLocator(),
) : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    private var downloading = ""

    @Volatile
    private var progress = 0

    @Volatile private var downloadedBytes = 0L

    @Volatile private var totalBytes = 0L

    @Volatile private var downloadStage = "download"

    @Volatile
    private var failure = ""

    @Volatile
    private var warming = ""
    private var managedServer: ProcessHandle? = null

    fun status(): EngineStatus {
        var online = true
        val installed = try {
            ollama.installed()
        } catch (exception: IntegrationException) {
            online = false
            emptySet()
        }
        return EngineStatus(
            online, catalog(installed), downloading, progress, failure, warming,
            downloadedBytes, totalBytes, downloadStage,
        )
    }

    fun start() {
        val executable = directory.resolve("engine/ollama.exe").toAbsolutePath().normalize()
        if (status().online) {
            managedServer = processes.find(executable)
            return
        }
        if (managedServer?.isAlive == true || !Files.exists(executable)) return
        val logs = directory.resolve("engine.log").toFile()
        managedServer = ProcessBuilder(executable.toString(), "serve")
            .redirectOutput(ProcessBuilder.Redirect.appendTo(logs))
            .redirectError(ProcessBuilder.Redirect.appendTo(logs))
            .start().toHandle()
    }

    fun warm(model: String) {
        executor.submit {
            try {
                if (model !in ollama.installed()) {
                    return@submit
                }
                warming = model
                ollama.warm(model)
            } catch (exception: IntegrationException) {
                if (warming.isNotBlank()) {
                    failure = EngineWarmupFailedException().message
                }
                System.err.println("Model warmup unavailable: ${exception.javaClass.simpleName}")
            } finally {
                warming = ""
            }
        }
    }

    @Synchronized
    fun installEngine() {
        if (downloading.isNotBlank()) {
            throw DownloadAlreadyRunningException()
        }
        downloading = "Ollama"
        downloadedBytes = 0
        totalBytes = 0
        downloadStage = "download"
        failure = ""
        progress = 0
        executor.submit {
            try {
                val root = directory.resolve("engine").toAbsolutePath().normalize()
                Files.createDirectories(root)
                val url = "https://github.com/ollama/ollama/releases/download/v$OLLAMA_VERSION/ollama-windows-amd64.zip"
                val request = HttpRequest.newBuilder(URI.create(url)).build()
                val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
                val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
                response.body().use { input ->
                    if (response.statusCode() != 200) {
                        throw EngineDownloadFailedException(response.statusCode())
                    }
                    val archive = directory.resolve("engine-download.zip")
                    downloadWriter.copy(
                        input,
                        archive,
                        response.headers().firstValueAsLong("Content-Length").orElse(0),
                    ) {
                            received,
                            total,
                        ->
                        downloadedBytes = received
                        totalBytes = total
                        progress = if (total > 0) (received * 85 / total).toInt().coerceIn(0, 85) else 0
                    }
                    downloadStage = "extract"
                    progress = 90
                    ZipInputStream(Files.newInputStream(archive)).use { zip ->
                        generateSequence { zip.nextEntry }.forEach { entry ->
                            val target = root.resolve(entry.name).normalize()
                            if (!target.startsWith(root)) {
                                throw InvalidEngineArchiveException()
                            }
                            if (entry.isDirectory) {
                                Files.createDirectories(target)
                            } else {
                                Files.createDirectories(target.parent)
                                Files.copy(zip, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                            }
                        }
                    }
                    Files.delete(archive)
                }
                progress = 100
                start()
            } catch (exception: Exception) {
                failure = EngineInstallFailedException().message
                System.err.println("Engine installation failed: ${exception.javaClass.simpleName}")
            } finally {
                downloading = ""
            }
        }
    }

    @Synchronized
    fun pull(model: String) {
        if (catalog(emptySet()).stream().noneMatch { it.id == model }) {
            throw UnsupportedModelException()
        }
        if (downloading.isNotBlank()) {
            throw DownloadAlreadyRunningException()
        }
        downloading = model
        downloadedBytes = 0
        totalBytes = 0
        downloadStage = "download"
        progress = 0
        failure = ""
        executor.submit {
            try {
                try {
                    ollama.pull(model) { progress = maxOf(progress, it).coerceAtMost(99) }
                } catch (exception: RegistryRedirectException) {
                    installer.install(model) { progress = maxOf(progress, it).coerceAtMost(99) }
                }
                progress = 100
                warm(model)
            } catch (exception: Exception) {
                failure = ModelPullInterruptedException().message
                System.err.println("Model download failed: ${exception.javaClass.simpleName}")
            } finally {
                downloading = ""
            }
        }
    }

    private fun catalog(installed: Set<String>): List<ModelInfo> = models.installed(installed)

    override fun close() {
        executor.shutdownNow()
        ollama.releaseModels()
        val active = managedServer ?: return
        active.descendants().use { descendants -> descendants.forEach { it.destroy() } }
        active.destroy()
        try {
            active.onExit().get(3, java.util.concurrent.TimeUnit.SECONDS)
        } catch (exception: java.util.concurrent.TimeoutException) {
            active.descendants().use { descendants -> descendants.forEach { it.destroyForcibly() } }
            active.destroyForcibly()
        }
    }
}
