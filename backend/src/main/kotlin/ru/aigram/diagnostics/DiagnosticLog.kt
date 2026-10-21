package ru.aigram.diagnostics

import jakarta.inject.Inject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode
import ru.aigram.resources.ResourceCatalog
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

private const val MAX_ERRORS = 100
private const val MAX_LOG_BYTES = 1_048_576L
private const val MAX_STACK_FRAMES = 8L
private const val APPLICATION_PACKAGE = "ru.aigram."
private val UPDATE_CODES = mapOf(
    "check" to "UPDATE_CHECK_FAILED",
    "download" to "UPDATE_DOWNLOAD_FAILED",
    "install" to "UPDATE_INSTALL_FAILED",
)

/** A bounded support log. Exception messages, requests, prompts and chat identifiers are never persisted. */
class DiagnosticLog @Inject constructor(directory: Path, private val clock: Clock, resources: ResourceCatalog) {
    private val path = directory.resolve("diagnostics/errors.json")
    private val updatePath = directory.resolve("desktop/updates/last-error.json")
    private val json = Json { prettyPrint = true }
    private val guides = Json.decodeFromString<Map<String, DiagnosticGuide>>(
        resources.text("diagnostics/ru/guides.json"),
    )
    private var entries = load()
    private var updateModified = 0L

    @Synchronized
    fun report(): DiagnosticReport {
        importUpdateFailure()
        return DiagnosticReport(entries.sortedByDescending { it.date })
    }

    @Synchronized
    fun record(source: DiagnosticSource, exception: Throwable) {
        val code = (exception as? AppException)?.code ?: ErrorCode.REQUEST_FAILED
        val type = exception.javaClass.simpleName
        val previous = entries.firstOrNull {
            it.source == source.value && it.code == code.name &&
                it.exceptionType == type
        }
        val guide = guides[code.name] ?: guides.getValue(source.value)
        val now = clock.instant().epochSecond
        val metadata = buildMap {
            put("status", code.status.toString())
            exception.cause?.let { put("causeType", it.javaClass.simpleName) }
            val frames = exception.stackTrace.toList().stream()
                .filter { it.className.startsWith(APPLICATION_PACKAGE) }
                .limit(MAX_STACK_FRAMES)
                .map { it.className.removePrefix(APPLICATION_PACKAGE) + "." + it.methodName + ":" + it.lineNumber }
                .toList()
            if (frames.isNotEmpty()) put("location", frames.joinToString("\n"))
        }
        val entry = DiagnosticEntry(
            id = previous?.id ?: UUID.randomUUID().toString(),
            date = now,
            firstOccurredAt = previous?.firstOccurredAt ?: now,
            source = source.value,
            code = code.name,
            message = guide.message,
            remediation = guide.remediation,
            exceptionType = type,
            occurrences = (previous?.occurrences ?: 0) + 1,
            metadata = metadata,
        )
        entries = (listOf(entry) + entries.filter { it.id != entry.id }).take(MAX_ERRORS)
        persist()
    }

    @Synchronized
    fun clear() {
        entries = emptyList()
        try {
            Files.deleteIfExists(updatePath)
        } catch (exception: IOException) {
            System.err.println("Update diagnostic could not be cleared: ${exception.javaClass.simpleName}")
        }
        persist()
    }

    private fun load(): List<DiagnosticEntry> = try {
        if (Files.isRegularFile(path) && Files.size(path) <= MAX_LOG_BYTES) {
            json.decodeFromString<DiagnosticReport>(Files.readString(path)).errors.take(MAX_ERRORS)
        } else {
            emptyList()
        }
    } catch (exception: IOException) {
        System.err.println("Diagnostic history unavailable: ${exception.javaClass.simpleName}")
        emptyList()
    } catch (exception: SerializationException) {
        System.err.println("Diagnostic history invalid: ${exception.javaClass.simpleName}")
        emptyList()
    }

    private fun persist() {
        try {
            Files.createDirectories(path.parent)
            val temporary = path.resolveSibling("errors.tmp")
            Files.writeString(temporary, json.encodeToString(DiagnosticReport(entries)))
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
        } catch (exception: IOException) {
            System.err.println("Diagnostic history could not be saved: ${exception.javaClass.simpleName}")
        }
    }

    private fun importUpdateFailure() {
        try {
            if (!Files.isRegularFile(updatePath) || Files.size(updatePath) > MAX_LOG_BYTES) return
            val modified = Files.getLastModifiedTime(updatePath).toMillis()
            if (modified == updateModified) return
            updateModified = modified
            val failure = json.decodeFromString<DesktopUpdateFailure>(
                Files.readString(updatePath).removePrefix("\uFEFF"),
            )
            if (UPDATE_CODES[failure.stage] != failure.code) return
            val date = Instant.parse(failure.failedAt).epochSecond
            val previous = entries.firstOrNull { it.source == DiagnosticSource.UPDATE.value && it.code == failure.code }
            if (previous != null && previous.date >= date) return
            val guide = guides.getValue(failure.code)
            val entry = DiagnosticEntry(
                previous?.id ?: UUID.randomUUID().toString(), date, previous?.firstOccurredAt ?: date,
                DiagnosticSource.UPDATE.value, failure.code, guide.message, guide.remediation, "DesktopUpdater",
                (previous?.occurrences ?: 0) + 1, mapOf("stage" to failure.stage),
            )
            entries = (listOf(entry) + entries.filter { it.id != entry.id }).take(MAX_ERRORS)
            persist()
        } catch (exception: IOException) {
            System.err.println("Update diagnostic unavailable: ${exception.javaClass.simpleName}")
        } catch (exception: SerializationException) {
            System.err.println("Update diagnostic invalid: ${exception.javaClass.simpleName}")
        } catch (exception: DateTimeParseException) {
            System.err.println("Update diagnostic timestamp invalid: ${exception.javaClass.simpleName}")
        }
    }
}
