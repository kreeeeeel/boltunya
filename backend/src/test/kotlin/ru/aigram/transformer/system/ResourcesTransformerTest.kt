package ru.aigram.transformer.system

import org.junit.jupiter.api.Test
import ru.aigram.domain.system.CacheStatistics
import kotlin.test.assertEquals

class ResourcesTransformerTest {
    @Test
    fun `cache response retains byte categories without truncation`() {
        val value = CacheStatistics(9_000_000_000L, 100, 4_000_000_000L, 3_000_000_000L, 1_000_000_000L, 1_000_000_000L)
        val mapped = value.toContract()

        assertEquals(value.totalBytes, mapped.totalBytes)
        assertEquals(value.photoBytes, mapped.photoBytes)
        assertEquals(value.videoBytes, mapped.videoBytes)
        assertEquals(value.documentBytes, mapped.documentBytes)
    }
}
