package ru.boltunya.ai

import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.stream.Stream

class FakeEngineProcess(
    private val processId: Long,
    private val gracefulExit: Boolean = true,
    private val descendants: List<ProcessHandle> = emptyList(),
) : ProcessHandle {
    private var alive = true
    private val exited = CompletableFuture<ProcessHandle>()
    var forcedStops = 0
        private set

    override fun pid(): Long = processId
    override fun isAlive(): Boolean = alive
    override fun parent(): Optional<ProcessHandle> = Optional.empty()
    override fun children(): Stream<ProcessHandle> = descendants()
    override fun descendants(): Stream<ProcessHandle> = if (alive) descendants.stream() else Stream.empty()
    override fun onExit(): CompletableFuture<ProcessHandle> = exited
    override fun supportsNormalTermination(): Boolean = true
    override fun compareTo(other: ProcessHandle): Int = processId.compareTo(other.pid())
    override fun info(): ProcessHandle.Info = throw UnsupportedOperationException()

    override fun destroy(): Boolean {
        if (gracefulExit) {
            alive = false
            exited.complete(this)
        }
        return gracefulExit
    }

    override fun destroyForcibly(): Boolean {
        forcedStops += 1
        alive = false
        exited.complete(this)
        return true
    }
}
