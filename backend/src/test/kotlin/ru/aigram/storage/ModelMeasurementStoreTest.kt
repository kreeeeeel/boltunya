package ru.aigram.storage

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.ai.model.ModelMeasurement
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelMeasurementStoreTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `measurements survive restart and remain isolated by model`() {
        val store = ModelMeasurementStore(directory)
        assertNull(store.get("deepseek-r1:8b"))
        store.record("deepseek-r1:8b", ModelMeasurement(1000, 30.5))
        store.record("gemma3:4b", ModelMeasurement(500, 60.0))
        store.record("deepseek-r1:8b", ModelMeasurement(900, 32.0))
        val restored = ModelMeasurementStore(directory)
        assertEquals(ModelMeasurement(900, 32.0), restored.get("deepseek-r1:8b"))
        assertEquals(ModelMeasurement(500, 60.0), restored.get("gemma3:4b"))
    }
}
