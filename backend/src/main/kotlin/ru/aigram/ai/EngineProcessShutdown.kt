package ru.aigram.ai

import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private const val SHUTDOWN_SECONDS = 3L

internal fun terminateEngineProcess(server: ProcessHandle) {
    val descendants = server.descendants().use { it.toList() }
    descendants.stream().forEach { it.destroy() }
    server.destroy()
    try {
        server.onExit().get(SHUTDOWN_SECONDS, TimeUnit.SECONDS)
    } catch (_: TimeoutException) {
        server.destroyForcibly()
    } finally {
        // A child can become orphaned as soon as its parent exits.
        descendants.stream().filter { it.isAlive }.forEach { it.destroyForcibly() }
    }
}
