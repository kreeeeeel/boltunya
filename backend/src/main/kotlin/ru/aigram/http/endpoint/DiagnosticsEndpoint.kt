package ru.aigram.http.endpoint

import jakarta.inject.Inject
import ru.aigram.diagnostics.DiagnosticLog
import ru.aigram.storage.StorageLocations

class DiagnosticsEndpoint @Inject constructor(
    private val errors: DiagnosticLog,
    private val storage: StorageLocations,
) {
    fun errors() = errors.report()
    fun clear() = errors.clear()
    fun locations() = storage.report()
}
