package ru.aigram.diagnostics

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode
import ru.aigram.exception.impl.EngineUnavailableException
import ru.aigram.resources.ResourceCatalog
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticLogTest {
    @TempDir lateinit var directory: Path
    private val clock = Clock.fixed(Instant.parse("2026-10-20T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `keeps safe repeated errors across restarts without persisting exception messages`() {
        val log = DiagnosticLog(directory, clock, ResourceCatalog())
        val secret = "private message api_hash owner_name C:/private/path token=secret"
        log.record(DiagnosticSource.ENGINE, EngineUnavailableException(IllegalStateException(secret)))
        log.record(DiagnosticSource.ENGINE, EngineUnavailableException(IllegalStateException(secret)))
        val entry = log.report().errors.single()
        assertEquals("ENGINE_UNAVAILABLE", entry.code)
        assertEquals(2, entry.occurrences)
        assertEquals("IllegalStateException", entry.metadata["causeType"])
        assertFalse(Files.readString(directory.resolve("diagnostics/errors.json")).contains(secret))
        assertFalse(entry.metadata.values.any { it.contains("api_hash") })
        val restored = DiagnosticLog(directory, clock, ResourceCatalog())
        assertEquals(entry, restored.report().errors.single())
        restored.clear()
        assertTrue(DiagnosticLog(directory, clock, ResourceCatalog()).report().errors.isEmpty())
    }

    @Test
    fun `unknown failures get a safe explanation and broken history is recoverable`() {
        Files.createDirectories(directory.resolve("diagnostics"))
        Files.writeString(directory.resolve("diagnostics/errors.json"), "not json")
        val log = DiagnosticLog(directory, clock, ResourceCatalog())
        log.record(DiagnosticSource.REPLY, IllegalStateException("private user text"))
        val entry = log.report().errors.single()
        assertEquals("REQUEST_FAILED", entry.code)
        assertEquals("reply", entry.source)
        assertTrue(entry.remediation.isNotBlank())
        assertFalse(entry.message.contains("private"))
    }

    @Test
    fun `imports PowerShell UTF8 BOM updater errors once and clears their source file`() {
        val update = directory.resolve("desktop/updates/last-error.json")
        Files.createDirectories(update.parent)
        Files.writeString(
            update,
            "\uFEFF" + """{"failedAt":"2026-10-20T09:00:00Z","stage":"download","code":"UPDATE_DOWNLOAD_FAILED"}""",
        )
        val log = DiagnosticLog(directory, clock, ResourceCatalog())
        assertEquals("UPDATE_DOWNLOAD_FAILED", log.report().errors.single().code)
        assertEquals(1, log.report().errors.single().occurrences)
        assertEquals(1, DiagnosticLog(directory, clock, ResourceCatalog()).report().errors.single().occurrences)
        log.clear()
        assertFalse(Files.exists(update))
        assertTrue(log.report().errors.isEmpty())
    }

    @Test
    fun `ignores unexpected updater codes instead of exposing arbitrary file content`() {
        val update = directory.resolve("desktop/updates/last-error.json")
        Files.createDirectories(update.parent)
        Files.writeString(update, """{"failedAt":"2026-10-20T09:00:00Z","stage":"download","code":"secret"}""")
        assertTrue(DiagnosticLog(directory, clock, ResourceCatalog()).report().errors.isEmpty())
    }

    @Test
    fun `retains at most one hundred grouped errors`() {
        val log = DiagnosticLog(directory, clock, ResourceCatalog())
        val codes = ErrorCode.entries.stream().limit(110).toList()
        codes.stream().forEach { log.record(DiagnosticSource.HTTP, AppException(it)) }
        val errors = log.report().errors
        assertEquals(100, errors.size)
        assertFalse(errors.any { it.code == codes.first().name })
        assertTrue(errors.any { it.code == codes.last().name })
        assertEquals(errors, DiagnosticLog(directory, clock, ResourceCatalog()).report().errors)
    }
}
