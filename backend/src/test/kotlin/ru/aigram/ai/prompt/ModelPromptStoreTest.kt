package ru.aigram.ai.prompt

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.profile.ResolvedModelTarget
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelPromptStoreTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `persists instructions separately for local models and profile model identities`() {
        val local = ResolvedModelTarget(model = "qwen3:1.7b", baseUrl = "http://localhost", remote = false)
        val api = local.copy(profileId = "profile", model = "remote-model")
        val store = ModelPromptStore(directory)
        store.save(ModelPromptSettings(local.model, instructions = "Короткие ответы", contextWindow = 16384))
        store.save(ModelPromptSettings(api.model, api.profileId, instructions = "Деловые ответы"))
        val reopened = ModelPromptStore(directory)
        assertEquals("Короткие ответы", reopened.get(local).instructions)
        assertEquals(16384, reopened.configure(local).options.contextWindow)
        assertEquals("Деловые ответы", reopened.get(api).instructions)
        assertTrue(reopened.get(api.copy(model = "different-model")).instructions.isEmpty())
        assertTrue(reopened.get(local.copy(model = "gemma3:4b")).instructions.isEmpty())
        assertEquals(2, reopened.list().size)
    }
}
