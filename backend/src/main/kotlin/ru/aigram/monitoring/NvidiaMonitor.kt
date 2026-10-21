package ru.aigram.monitoring

import jakarta.inject.Inject
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val GPU_TIMEOUT_SECONDS = 2L
private const val MEBIBYTE = 1024L * 1024

class NvidiaMonitor @Inject constructor() {
    private var unavailable = false

    fun sample(): GpuMetrics? {
        if (unavailable) return null
        val process = try {
            ProcessBuilder(
                "nvidia-smi.exe",
                "--query-gpu=utilization.gpu,memory.used,memory.total",
                "--format=csv,noheader,nounits",
            ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        } catch (_: IOException) {
            unavailable = true
            return null
        }
        if (!process.waitFor(GPU_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        if (process.exitValue() != 0) return null
        return parse(process.inputStream.bufferedReader().use { it.readLine() }.orEmpty())
    }

    internal fun parse(output: String): GpuMetrics? {
        val row = output.lineSequence().firstOrNull().orEmpty().split(',').map(String::trim)
        val percent = row.getOrNull(0)?.toDoubleOrNull() ?: return null
        val used = row.getOrNull(1)?.toDoubleOrNull() ?: return null
        val total = row.getOrNull(2)?.toDoubleOrNull() ?: return null
        if (!percent.isFinite() || percent !in 0.0..100.0 || !used.isFinite() || !total.isFinite()) return null
        if (used < 0 || total <= 0 || used > total) return null
        return GpuMetrics(percent, (used * MEBIBYTE).toLong(), (total * MEBIBYTE).toLong())
    }
}
