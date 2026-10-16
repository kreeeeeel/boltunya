package ru.boltunya.ai

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EngineProcessShutdownTest {
    @Test
    fun `forces a captured child to stop even when the server exits before its child does`() {
        val runner = FakeEngineProcess(2, gracefulExit = false)
        val server = FakeEngineProcess(1, descendants = listOf(runner))

        terminateEngineProcess(server)

        assertFalse(server.isAlive)
        assertFalse(runner.isAlive)
        assertEquals(1, runner.forcedStops)
    }
}
