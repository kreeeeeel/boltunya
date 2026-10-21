package ru.aigram.storage

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.AppearanceSettings
import ru.aigram.exception.impl.InvalidAppearanceException
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppearanceStoreTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `persists complete appearance across restarts`() {
        val value = AppearanceSettings("dark", mapOf("accent" to "#ffd166"), ".message { border-radius: 16px; }")
        AppearanceStore(directory, Json).save(value)
        assertEquals(value, AppearanceStore(directory, Json).get())
    }

    @Test
    fun `invalid values cannot overwrite saved appearance`() {
        val store = AppearanceStore(directory, Json)
        val saved = store.save(AppearanceSettings("light"))
        listOf(
            AppearanceSettings("invalid"),
            AppearanceSettings(tokens = mapOf("unknown" to "#fff")),
            AppearanceSettings(tokens = mapOf("accent" to "red;display:none")),
            AppearanceSettings(css = "x".repeat(20_001)),
        ).stream().forEach { value -> assertFailsWith<InvalidAppearanceException> { store.save(value) } }
        assertEquals(saved, store.get())
    }
}
