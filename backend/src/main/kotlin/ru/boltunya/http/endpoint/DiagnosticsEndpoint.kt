package ru.boltunya.http.endpoint

import jakarta.inject.Inject
import ru.boltunya.diagnostics.DiagnosticLog
import ru.boltunya.storage.StorageLocations

class DiagnosticsEndpoint @Inject constructor(
    private val errors: DiagnosticLog,
    private val storage: StorageLocations,
) {
    fun errors() = errors.report()
    fun clear() = errors.clear()
    fun locations() = storage.report()
}
