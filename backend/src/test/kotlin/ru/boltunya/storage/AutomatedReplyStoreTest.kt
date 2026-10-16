package ru.boltunya.storage

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutomatedReplyStoreTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `retains old generated ids beyond five thousand replies`() {
        Files.writeString(directory.resolve("automated-replies.json"), Json.encodeToString((1..5001).map { "42:$it" }))
        AutomatedReplyStore(directory).record("42", "5002")
        val restored = AutomatedReplyStore(directory)
        assertTrue(restored.contains("42", "1"))
        assertTrue(restored.contains("42", "5002"))
    }

    @Test
    fun `remembers generated message ids across restarts without storing conversation text`() {
        AutomatedReplyStore(directory).record("42", "100")
        val restored = AutomatedReplyStore(directory)
        assertTrue(restored.contains("42", "100"))
        assertFalse(restored.contains("43", "100"))
        assertFalse(restored.contains("42", "101"))
    }
}
