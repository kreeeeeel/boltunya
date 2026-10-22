package ru.aigram.ai

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.IntStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EngineWarmStateTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `paused automation rejects warmup and cancels an owned warmup before resuming`() {
        val enabled = AtomicBoolean()
        ControlledWarmServer().start().use { server ->
            EngineManager(directory, server.runtime, automaticEnabled = enabled::get).use { engine ->
                try {
                    engine.warm("qwen3:1.7b")
                    assertEquals("", engine.status().warming)
                    assertEquals(0, server.count("qwen3:1.7b"))

                    enabled.set(true)
                    engine.warm("qwen3:1.7b")
                    assertTrue(server.awaitWarm("qwen3:1.7b"))
                    enabled.set(false)
                    engine.pauseInference()
                    assertEquals("", engine.status().warming)
                    engine.warm("gemma3:4b")
                    assertEquals(0, server.count("gemma3:4b"))
                    server.release("qwen3:1.7b")

                    enabled.set(true)
                    engine.warm("gemma3:4b")
                    assertTrue(server.awaitWarm("gemma3:4b"))
                    server.release("gemma3:4b")
                    assertTrue(await { engine.status().warming.isEmpty() })
                    assertEquals(1, server.count("qwen3:1.7b"))
                    assertEquals(1, server.count("gemma3:4b"))
                } finally {
                    server.release("qwen3:1.7b")
                    server.release("gemma3:4b")
                }
            }
        }
    }

    @Test
    fun `warmup is visible immediately and duplicate calls for the active model do not queue another request`() {
        ControlledWarmServer().start().use { server ->
            EngineManager(directory, server.runtime).use { engine ->
                try {
                    engine.warm("qwen3:1.7b")
                    assertEquals("qwen3:1.7b", engine.status().warming)
                    assertTrue(server.awaitWarm("qwen3:1.7b"))
                    engine.warm("qwen3:1.7b")
                    engine.warm("qwen3:1.7b")
                    server.release("qwen3:1.7b")
                    assertTrue(await { engine.status().warming.isEmpty() })
                    engine.warm("gemma3:4b")
                    assertTrue(server.awaitWarm("gemma3:4b"))
                    assertEquals(1, server.count("qwen3:1.7b"))
                    server.release("gemma3:4b")
                    assertTrue(await { engine.status().warming.isEmpty() })
                } finally {
                    server.release("qwen3:1.7b")
                    server.release("gemma3:4b")
                }
            }
        }
    }

    @Test
    fun `completion of an older warmup cannot clear the newer model warming status`() {
        ControlledWarmServer().start().use { server ->
            EngineManager(directory, server.runtime).use { engine ->
                try {
                    engine.warm("qwen3:1.7b")
                    assertTrue(server.awaitWarm("qwen3:1.7b"))
                    engine.warm("gemma3:4b")
                    assertEquals("gemma3:4b", engine.status().warming)
                    server.release("qwen3:1.7b")
                    assertTrue(server.awaitWarm("gemma3:4b"))
                    assertEquals("gemma3:4b", engine.status().warming)
                    server.release("gemma3:4b")
                    assertTrue(await { engine.status().warming.isEmpty() })
                    assertEquals(1, server.count("qwen3:1.7b"))
                    assertEquals(1, server.count("gemma3:4b"))
                } finally {
                    server.release("qwen3:1.7b")
                    server.release("gemma3:4b")
                }
            }
        }
    }

    @Test
    fun `failure of an older warmup does not mark the newer model as failed`() {
        ControlledWarmServer().start().use { server ->
            EngineManager(directory, server.runtime).use { engine ->
                try {
                    engine.warm("qwen3:1.7b")
                    assertTrue(server.awaitWarm("qwen3:1.7b"))
                    engine.warm("gemma3:4b")
                    server.release("qwen3:1.7b", code = 500)
                    assertTrue(server.awaitWarm("gemma3:4b"))
                    val state = engine.status()
                    assertEquals("gemma3:4b", state.warming)
                    assertEquals("", state.error)
                    server.release("gemma3:4b")
                    assertTrue(await { engine.status().warming.isEmpty() })
                    assertEquals("", engine.status().error)
                } finally {
                    server.release("qwen3:1.7b")
                    server.release("gemma3:4b")
                }
            }
        }
    }

    private fun await(condition: () -> Boolean): Boolean = IntStream.range(0, 300).anyMatch {
        if (condition()) {
            true
        } else {
            TimeUnit.MILLISECONDS.sleep(10)
            false
        }
    }
}
