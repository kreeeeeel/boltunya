package ru.aigram.ai.profile

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelRoutingMigrationTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `retired research assignments leave saved reply memory and vision routing unchanged`() {
        val file = directory.resolve("models/routing.json")
        Files.createDirectories(file.parent)
        Files.writeString(
            file,
            """
            {"enabled":true,"replyModel":"reply:local","memoryModel":"memory:local",
             "visionModel":"vision:local","researchProfileId":"old-profile","researchModel":"old:model"}
            """.trimIndent(),
        )
        val store = ModelRoutingStore(directory)

        assertTrue(store.get().enabled)
        assertEquals("reply:local", store.get().replyModel)
        assertEquals("memory:local", store.get().memoryModel)
        assertEquals("vision:local", store.get().visionModel)
        store.save(store.get())
        assertFalse(Files.readString(file).contains("research"))
        assertEquals(store.get(), ModelRoutingStore(directory).get())
    }
}
