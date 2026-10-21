package ru.aigram.ai

import jakarta.inject.Inject
import ru.aigram.diagnostics.DiagnosticLog
import ru.aigram.diagnostics.DiagnosticSource
import ru.aigram.domain.ComputeDevice
import ru.aigram.domain.ComputeDeviceStatus
import ru.aigram.domain.EngineStatus
import ru.aigram.domain.ModelInfo
import ru.aigram.exception.IntegrationException
import ru.aigram.exception.InvalidInputException
import ru.aigram.exception.impl.DownloadAlreadyRunningException
import ru.aigram.exception.impl.EngineDownloadFailedException
import ru.aigram.exception.impl.EngineInstallFailedException
import ru.aigram.exception.impl.EngineWarmupFailedException
import ru.aigram.exception.impl.InvalidEngineArchiveException
import ru.aigram.exception.impl.ModelPullInterruptedException
import ru.aigram.exception.impl.RegistryRedirectException
import ru.aigram.exception.impl.UnsupportedModelException
import ru.aigram.storage.ModelMeasurementStore
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
    private val ollama: OllamaRuntime,
    private val models: ModelCatalog = ModelCatalog(),
    private val installer: RegistryModelInstaller = RegistryModelInstaller(),
    private val downloadWriter: DownloadWriter = DownloadWriter(),
    private val processes: EngineProcessLocator = EngineProcessLocator(),
    private val measurements: ModelMeasurementStore = ModelMeasurementStore(directory),
    private val inference: ModelInference = ModelInference(),
    private val installation: OllamaInstallation = OllamaInstallation(directory),
    private val localModels: OllamaModelInventory = OllamaModelInventory(),
    private val diagnostics: DiagnosticLog? = null,
    private val devices: OllamaDeviceSelector? = null,
) : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    private var downloading = ""

    @Volatile
    private var progress = 0

    @Volatile private var downloadedBytes = 0L

    @Volatile private var totalBytes = 0L

    @Volatile private var downloadStage = "download"

    @Volatile private var cancellation: DownloadCancellation? = null

    @Volatile
    private var failure = ""

    @Volatile
    private var warming = ""

    @Volatile private var managedServer: ProcessHandle? = null

    @Volatile private var activeDevice: String? = null

    fun status(): EngineStatus {
        var online = true
        val installed = try {
            ollama.installed()
        } catch (exception: IntegrationException) {
            online = false
            if (ollama.isLocalServer) localModels.installed() else emptySet()
        }
        return EngineStatus(
            online, catalog(installed), downloading, progress, failure, warming,
            downloadedBytes, totalBytes, downloadStage,
            installed = online || (ollama.isLocalServer && installation.executable() != null),
        )
    }

    fun devices(): ComputeDeviceStatus {
        val online = status().online
        val managed = managedServer?.isAlive == true
        return devices?.status(
            online,
            ollama.isLocalServer,
            ollama.isDefaultLocalServer,
            installation.executable() != null,
            managed,
            activeDevice,
        ) ?: ComputeDeviceStatus(
            "auto",
            null,
            managed,
            false,
            false,
            "external",
            listOf(ComputeDevice("auto", "Auto", "auto"), ComputeDevice("cpu", "CPU", "cpu")),
        )
    }

    @Synchronized
    fun start() {
        if (status().online) {
            if (ollama.isDefaultLocalServer && managedServer?.isAlive != true) {
                managedServer = processes.find(directory.resolve("engine/ollama.exe").toAbsolutePath().normalize())
                activeDevice = null
            }
            return
        }
        if (managedServer?.isAlive == true || !ollama.isDefaultLocalServer) return
        val executable = installation.executable() ?: return
        val selected = devices?.selected() ?: "auto"
        managedServer = prepareProcess(executable, selected).start().toHandle()
        activeDevice = selected
    }

    internal fun prepareProcess(executable: Path, selected: String = devices?.selected() ?: "auto"): ProcessBuilder {
        val logs = directory.resolve("engine.log").toFile()
        val process = ProcessBuilder(executable.toString(), "serve")
            .redirectOutput(ProcessBuilder.Redirect.appendTo(logs))
            .redirectError(ProcessBuilder.Redirect.appendTo(logs))
        process.environment().putAll(devices?.environment(selected).orEmpty())
        return process
    }

    fun warm(model: String) {
        executor.submit {
            try {
                if (model !in ollama.installed()) {
                    return@submit
                }
                warming = model
                inference.foreground { ollama.warm(model) }
            } catch (exception: IntegrationException) {
                diagnostics?.record(DiagnosticSource.ENGINE, exception)
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
        val task = DownloadCancellation()
        cancellation = task
        executor.submit {
            try {
                task.begin()
                val root = directory.resolve("engine").toAbsolutePath().normalize()
                Files.createDirectories(root)
                val url = "https://github.com/ollama/ollama/releases/download/v$OLLAMA_VERSION/ollama-windows-amd64.zip"
                val request = HttpRequest.newBuilder(URI.create(url)).build()
                val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
                val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
                response.body().use { input ->
                    task.attach(input)
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
                        task.check()
                        downloadedBytes = received
                        totalBytes = total
                        progress = if (total > 0) (received * 85 / total).toInt().coerceIn(0, 85) else 0
                    }
                    downloadStage = "extract"
                    progress = 90
                    ZipInputStream(Files.newInputStream(archive)).use { zip ->
                        generateSequence { zip.nextEntry }.forEach { entry ->
                            task.check()
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
                if (!task.isCancelled()) {
                    diagnostics?.record(DiagnosticSource.ENGINE, exception)
                    failure = EngineInstallFailedException().message
                    System.err.println("Engine installation failed: ${exception.javaClass.simpleName}")
                }
            } finally {
                finishDownload(task)
                Thread.interrupted()
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
        val task = DownloadCancellation()
        cancellation = task
        executor.submit {
            try {
                task.begin()
                try {
                    ollama.pull(model, task) { progress = maxOf(progress, it).coerceAtMost(99) }
                } catch (exception: RegistryRedirectException) {
                    installer.install(model) {
                        task.check()
                        progress = maxOf(progress, it).coerceAtMost(99)
                    }
                }
                task.check()
                progress = 100
                warm(model)
            } catch (exception: Exception) {
                if (!task.isCancelled()) {
                    diagnostics?.record(DiagnosticSource.ENGINE, exception)
                    failure = ModelPullInterruptedException().message
                    System.err.println("Model download failed: ${exception.javaClass.simpleName}")
                }
            } finally {
                finishDownload(task)
                Thread.interrupted()
            }
        }
    }

    private fun catalog(installed: Set<String>): List<ModelInfo> = models.installed(installed).map {
        it.copy(measurement = measurements.get(it.id))
    }

    @Synchronized
    fun cancelDownload() {
        cancellation?.cancel()
    }

    @Synchronized
    private fun finishDownload(task: DownloadCancellation) {
        if (cancellation !== task) return
        cancellation = null
        downloading = ""
    }

    override fun close() {
        cancelDownload()
        executor.shutdownNow()
        ollama.releaseModels()
        val active = managedServer ?: return
        terminateEngineProcess(active)
    }
}
