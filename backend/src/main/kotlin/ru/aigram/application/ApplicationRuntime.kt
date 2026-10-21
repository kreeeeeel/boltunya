package ru.aigram.application

import jakarta.inject.Inject
import ru.aigram.ai.EngineManager
import ru.aigram.ai.profile.ModelTargetResolver
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.configuration.ApplicationConfiguration
import ru.aigram.diagnostics.DiagnosticLog
import ru.aigram.diagnostics.DiagnosticSource
import ru.aigram.http.LocalWebServer
import ru.aigram.memory.MemoryCoordinator
import ru.aigram.monitoring.MonitoringService
import ru.aigram.storage.CredentialStore
import ru.aigram.telegram.MediaPrefetcher
import ru.aigram.telegram.TelegramGateway
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicBoolean

class ApplicationRuntime @Inject constructor(
    private val configuration: ApplicationConfiguration,
    private val server: LocalWebServer,
    private val telegram: TelegramGateway,
    private val engine: EngineManager,
    private val replies: ReplyCoordinator,
    private val credentials: CredentialStore,
    private val monitor: MonitoringService,
    private val memory: MemoryCoordinator,
    private val prefetch: MediaPrefetcher,
    private val targets: ModelTargetResolver,
    private val diagnostics: DiagnosticLog,
) : AutoCloseable {
    private val directory = configuration.directory
    private val closed = AtomicBoolean()
    private var lockChannel: FileChannel? = null
    private var lock: FileLock? = null

    fun start(): Boolean {
        Files.createDirectories(directory)
        val channel = FileChannel.open(
            directory.resolve("app.lock"),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
        )
        lockChannel = channel
        lock = channel.tryLock()
        if (lock == null) return false
        Files.writeString(directory.resolve("launch.url"), server.start())
        monitor.start()
        engine.start()
        try {
            val warmModel = targets.startupWarmModel()
            if (warmModel != null) engine.warm(warmModel)
        } catch (exception: Exception) {
            diagnostics.record(DiagnosticSource.ENGINE, exception)
            System.err.println("Startup warmup skipped: ${exception.javaClass.simpleName}")
        }
        memory.start()
        prefetch.start()
        Thread.ofVirtual().start {
            try {
                if (credentials.exists()) telegram.restore()
            } catch (exception: Exception) {
                System.err.println("Session restore failed: ${exception.javaClass.simpleName}")
            }
        }
        return true
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        listOf(memory, prefetch, replies, server, engine, monitor, telegram).stream().forEach { service ->
            try {
                service.close()
            } catch (exception: Exception) {
                System.err.println(
                    "Shutdown failed for ${service.javaClass.simpleName}: ${exception.javaClass.simpleName}",
                )
            }
        }
        if (lock != null) Files.deleteIfExists(directory.resolve("launch.url"))
        lock?.release()
        lockChannel?.close()
    }
}
