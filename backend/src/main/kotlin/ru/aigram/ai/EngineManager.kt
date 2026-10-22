package ru.aigram.ai

import ru.aigram.domain.ai.ComputeDevice
import ru.aigram.domain.ai.ComputeDeviceStatus
import ru.aigram.domain.ai.EngineStatus
import ru.aigram.domain.ai.model.ModelInfo
import ru.aigram.exception.IntegrationException
import ru.aigram.exception.InvalidInputException
import ru.aigram.exception.impl.DownloadAlreadyRunningException
import ru.aigram.exception.impl.EngineDownloadFailedException
import ru.aigram.exception.impl.EngineInstallFailedException
import ru.aigram.exception.impl.EngineRestartUnavailableException
import ru.aigram.exception.impl.EngineUnavailableException
import ru.aigram.exception.impl.EngineWarmupFailedException
import ru.aigram.exception.impl.InvalidEngineArchiveException
import ru.aigram.exception.impl.ModelPullInterruptedException
import ru.aigram.exception.impl.RegistryRedirectException
import ru.aigram.exception.impl.UnsupportedModelException
import ru.aigram.storage.ModelMeasurementStore
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipInputStream

private const val OLLAMA_VERSION = "0.40.4"
private const val RESTART_SHUTDOWN_SECONDS = 3L

class EngineManager(
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
    private val devices: OllamaDeviceSelector? = null,
    private val startProcess: (ProcessBuilder) -> ProcessHandle = { it.start().toHandle() },
    private val automaticEnabled: () -> Boolean = { true },
) : AutoCloseable {
    private val closed = AtomicBoolean()
    private val executor = Executors.newSingleThreadExecutor()
    private val warmups = ScheduledThreadPoolExecutor(1).apply { removeOnCancelPolicy = true }
    private val warmupVersion = AtomicLong()
    private var warmup: Future<*>? = null

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
            if (ollama.isLocalServer) {
                localModels.installed()
            } else {
                emptySet()
            }
        }
        return EngineStatus(
            online, catalog(installed), downloading, progress, failure, warming,
            downloadedBytes, totalBytes, downloadStage,
            installed = online || (ollama.isLocalServer && installation.executable() != null),
            canRestart = ollama.isDefaultLocalServer && managedServer?.isAlive == true,
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
        if (closed.get()) {
            return
        }
        if (status().online) {
            if (ollama.isDefaultLocalServer && managedServer?.isAlive != true) {
                managedServer = processes.find(directory.resolve("engine/ollama.exe").toAbsolutePath().normalize())
                activeDevice = null
            }
            return
        }
        if (managedServer?.isAlive == true || !ollama.isDefaultLocalServer) {
            return
        }
        val executable = installation.executable()

        if (executable == null) {
            return
        }

        launch(executable)
    }

    @Synchronized
    fun restart() {
        val active = managedServer
        if (!ollama.isDefaultLocalServer || active?.isAlive != true) {
            throw EngineRestartUnavailableException()
        }
        val executable = installation.executable()
        if (executable == null) {
            throw EngineUnavailableException()
        }
        warmupVersion.incrementAndGet()
        warming = ""
        failure = ""
        terminateEngineProcess(active)
        try {
            active.onExit().get(RESTART_SHUTDOWN_SECONDS, TimeUnit.SECONDS)
        } catch (exception: TimeoutException) {
            throw EngineUnavailableException(exception)
        }
        managedServer = null
        activeDevice = null
        launch(executable)
    }

    private fun launch(executable: Path) {
        val selected = devices?.selected() ?: "auto"
        try {
            managedServer = startProcess(prepareProcess(executable, selected))
            activeDevice = selected
        } catch (exception: IOException) {
            throw EngineUnavailableException(exception)
        }
    }

    internal fun prepareProcess(executable: Path, selected: String = devices?.selected() ?: "auto"): ProcessBuilder {
        val process = ProcessBuilder(executable.toString(), "serve")
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
        process.environment().putAll(devices?.environment(selected).orEmpty())
        return process
    }

    @Synchronized
    fun warm(model: String) {
        if (closed.get() || !automaticEnabled() || warming == model) {
            return
        }
        warmup?.cancel(true)
        val version = warmupVersion.incrementAndGet()
        warming = model
        failure = ""
        warmup = warmups.submit {
            try {
                if (version != warmupVersion.get() || !automaticEnabled()) {
                    return@submit
                }
                if (model !in ollama.installed()) {
                    return@submit
                }
                inference.foreground {
                    if (version == warmupVersion.get() && automaticEnabled()) {
                        ollama.warm(model)
                    }
                }
            } catch (exception: IntegrationException) {
                synchronized(this@EngineManager) {
                    if (version == warmupVersion.get()) {
                        failure = EngineWarmupFailedException().message
                    }
                }
                System.err.println("Model warmup unavailable: ${exception.javaClass.simpleName}")
            } finally {
                synchronized(this@EngineManager) {
                    if (version == warmupVersion.get()) {
                        warming = ""
                    }
                }
            }
        }
    }

    @Synchronized
    fun pauseInference() {
        warmupVersion.incrementAndGet()
        warmup?.cancel(true)
        warmup = null
        warming = ""
        if (!closed.get()) {
            warmups.submit { ollama.releaseModels() }
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
                        progress = if (total > 0) {
                            (received * 85 / total).toInt().coerceIn(0, 85)
                        } else {
                            0
                        }
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
                if (automaticEnabled()) {
                    start()
                }
            } catch (exception: Exception) {
                if (!task.isCancelled()) {
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
        if (cancellation !== task) {
            return
        }
        cancellation = null
        downloading = ""
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        cancelDownload()
        executor.shutdownNow()
        warmups.shutdownNow()
        ollama.releaseModels()
        val active = managedServer

        if (active == null) {
            return
        }

        terminateEngineProcess(active)
    }
}
