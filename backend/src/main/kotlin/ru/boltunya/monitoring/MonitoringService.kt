package ru.boltunya.monitoring

import jakarta.inject.Inject
import oshi.SystemInfo
import oshi.software.os.OSProcess
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val HISTORY_SAMPLES = 90
private const val SAMPLE_INTERVAL_SECONDS = 2L
private const val PERCENT = 100.0

class MonitoringService @Inject constructor(private val gpu: NvidiaMonitor) : AutoCloseable {
    private val system = SystemInfo()
    private val cpu = system.hardware.processor
    private val interfaces = system.hardware.getNetworkIFs(false)
    private val executor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "resource-monitor").also {
            it.isDaemon =
                true
        }
    }
    private var ticks = cpu.systemCpuLoadTicks
    private var previousReceive = 0L
    private var previousSend = 0L
    private var previousTime = 0L
    private var previousProcesses: Map<Int, OSProcess> = emptyMap()
    private val ownPid = ProcessHandle.current().pid().toInt()
    private val parentPid = ProcessHandle.current().parent().map { it.pid().toInt() }.orElse(ownPid)

    @Volatile private var samples: List<MetricSample> = emptyList()

    fun start() {
        executor.scheduleWithFixedDelay({
            try {
                collect()
            } catch (
                exception: Exception,
            ) {
                System.err.println("Resource monitoring failed: ${exception.javaClass.simpleName}")
            }
        }, 0, SAMPLE_INTERVAL_SECONDS, TimeUnit.SECONDS)
    }

    fun history(): List<MetricSample> = samples

    private fun collect() {
        val now = System.nanoTime()
        val load = cpu.getSystemCpuLoadBetweenTicks(ticks).coerceIn(0.0, 1.0)
        ticks = cpu.systemCpuLoadTicks
        val memory = system.hardware.memory
        val processes = system.operatingSystem.processes
        val app = processes.stream().filter {
            it.processID == ownPid || it.processID == parentPid ||
                it.parentProcessID == parentPid
        }.toList()
        val ollama = processes.stream().filter {
            it.name.lowercase().startsWith("ollama")
        }.mapToLong { it.residentMemory }.sum()
        interfaces.stream().forEach { it.updateAttributes() }
        val received = interfaces.stream().mapToLong { it.bytesRecv }.sum()
        val sent = interfaces.stream().mapToLong { it.bytesSent }.sum()
        val elapsed = if (previousTime == 0L) 0.0 else (now - previousTime) / 1e9
        val receiveSpeed = if (elapsed > 0) ((received - previousReceive).coerceAtLeast(0) / elapsed).toLong() else 0
        val sendSpeed = if (elapsed > 0) ((sent - previousSend).coerceAtLeast(0) / elapsed).toLong() else 0
        previousReceive = received
        previousSend = sent
        previousTime = now
        val graphics = gpu.sample()
        val sample = MetricSample(
            Instant.now().toEpochMilli(), load * PERCENT,
            app.stream().mapToDouble {
                it.getProcessCpuLoadBetweenTicks(previousProcesses[it.processID]) /
                    cpu.logicalProcessorCount *
                    PERCENT
            }.sum().coerceIn(0.0, PERCENT),
            memory.total - memory.available, memory.total,
            app.stream().mapToLong { it.residentMemory }.sum(),
            processes.stream().filter { it.processID == ownPid }.mapToLong { it.residentMemory }.sum(),
            ollama, receiveSpeed, sendSpeed, graphics?.percent, graphics?.usedBytes, graphics?.totalBytes,
        )
        previousProcesses = processes.associateBy { it.processID }
        samples = (samples + sample).takeLast(HISTORY_SAMPLES)
    }

    override fun close() {
        executor.shutdownNow()
    }
}
