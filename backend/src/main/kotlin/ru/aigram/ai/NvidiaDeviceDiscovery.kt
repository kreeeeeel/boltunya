package ru.aigram.ai

import jakarta.inject.Inject
import ru.aigram.domain.ComputeDevice
import ru.aigram.validation.InputRule
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

private const val DISCOVERY_CACHE_MS = 30_000L
private const val QUERY_TIMEOUT_SECONDS = 2L
private const val MAX_QUERY_BYTES = 64 * 1024
private const val MEBIBYTE = 1024L * 1024
private const val MIN_COMPUTE_CAPABILITY = 5.0
private const val LEGACY_COMPUTE_CAPABILITY = 6.2
private const val MIN_DRIVER_MAJOR = 550
private const val MIN_LEGACY_DRIVER_MAJOR = 570
private const val QUERY_FIELDS = 5

class NvidiaDeviceDiscovery @Inject constructor(private val clock: Clock) : ComputeDeviceDiscovery {
    private var refreshedAt = Long.MIN_VALUE
    private var cached = emptyList<ComputeDevice>()

    @Synchronized
    override fun devices(): List<ComputeDevice> {
        val now = clock.millis()
        if (refreshedAt != Long.MIN_VALUE && now - refreshedAt < DISCOVERY_CACHE_MS) return cached
        cached = query()?.let(::parse).orEmpty()
        refreshedAt = now
        return cached
    }

    internal fun parse(output: String): List<ComputeDevice> = output.lines().stream().map { line ->
        val fields = line.split(',', limit = QUERY_FIELDS).map { it.trim().trim('"') }
        val uuid = fields.getOrNull(0).orEmpty()
        val memory = fields.getOrNull(1)?.toLongOrNull()
        val compute = fields.getOrNull(2)?.toDoubleOrNull()
        val driver = fields.getOrNull(3)?.substringBefore('.')?.toIntOrNull()
        val name = fields.getOrNull(4).orEmpty()
        if (!InputRule.GPU_UUID.accepts(uuid) || memory == null ||
            memory !in 1..Long.MAX_VALUE / MEBIBYTE || compute == null || !compute.isFinite() || driver == null ||
            name.isBlank()
        ) {
            null
        } else {
            val minimumDriver = if (compute <= LEGACY_COMPUTE_CAPABILITY) MIN_LEGACY_DRIVER_MAJOR else MIN_DRIVER_MAJOR
            val reason = when {
                compute < MIN_COMPUTE_CAPABILITY -> "compute"
                driver < minimumDriver -> "driver"
                else -> ""
            }
            ComputeDevice(uuid, name.take(256), "nvidia", memory * MEBIBYTE, compute >= MIN_COMPUTE_CAPABILITY, reason)
        }
    }.flatMap { device -> if (device == null) Stream.empty() else Stream.of(device) }.distinct().toList()

    private fun query(): String? {
        val process = try {
            ProcessBuilder(
                executable(),
                "--query-gpu=uuid,memory.total,compute_cap,driver_version,name",
                "--format=csv,noheader,nounits",
            ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        } catch (exception: IOException) {
            System.err.println("GPU discovery unavailable: ${exception.javaClass.simpleName}")
            return null
        }
        return try {
            if (!process.waitFor(QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS) || process.exitValue() != 0) {
                null
            } else {
                process.inputStream.use { String(it.readNBytes(MAX_QUERY_BYTES), Charsets.UTF_8) }
            }
        } catch (exception: IOException) {
            System.err.println("GPU discovery query failed: ${exception.javaClass.simpleName}")
            null
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private fun executable(): String {
        val windows = System.getenv("SystemRoot") ?: System.getenv("WINDIR")
        val systemTool = windows?.let { Path.of(it, "System32", "nvidia-smi.exe") }
        return if (systemTool != null && Files.isRegularFile(systemTool)) systemTool.toString() else "nvidia-smi.exe"
    }
}
