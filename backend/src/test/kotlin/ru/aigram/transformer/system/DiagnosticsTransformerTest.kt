package ru.aigram.transformer.system

import org.junit.jupiter.api.Test
import ru.aigram.contract.dto.DiagnosticSource
import ru.aigram.contract.dto.StorageLocationKind
import ru.aigram.domain.events.diagnostics.DiagnosticEntry
import ru.aigram.domain.events.diagnostics.DiagnosticReport
import ru.aigram.domain.system.storage.StorageLocation
import ru.aigram.domain.system.storage.StorageLocationReport
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DiagnosticsTransformerTest {
    @Test
    fun `diagnostic cards retain occurrences safe metadata and remediation`() {
        val value = DiagnosticEntry(
            "error-1", 4_000_000_000L, 3_900_000_000L, "update", "UPDATE_DOWNLOAD_FAILED",
            "Synthetic update failure", "Try again", "SyntheticException", 3, mapOf("stage" to "download"),
        )
        val mapped = DiagnosticsTransformer().toContract(DiagnosticReport(listOf(value))).errors.single()

        assertEquals(DiagnosticSource.UPDATE, mapped.source)
        assertEquals(value.date, mapped.date)
        assertEquals(3, mapped.occurrences)
        assertEquals("Try again", mapped.remediation)
        assertEquals(mapOf("stage" to "download"), mapped.metadata)
    }

    @Test
    fun `external model storage is described without inventing an openable local path`() {
        val value = StorageLocation(
            "ollama-models",
            "Models",
            "",
            available = false,
            openable = false,
            kind = "external",
            note = "Managed externally",
        )
        val mapped = StorageTransformer().toContract(StorageLocationReport(listOf(value))).locations.single()

        assertEquals(StorageLocationKind.EXTERNAL, mapped.kind)
        assertEquals("", mapped.path)
        assertFalse(mapped.available)
        assertFalse(mapped.openable)
        assertEquals("Managed externally", mapped.note)
    }
}
