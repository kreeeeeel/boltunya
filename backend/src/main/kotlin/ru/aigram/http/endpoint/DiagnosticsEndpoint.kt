package ru.aigram.http.endpoint

import jakarta.inject.Inject
import org.springframework.stereotype.Component
import ru.aigram.diagnostics.DiagnosticLog
import ru.aigram.storage.StorageLocations

@Component
class DiagnosticsEndpoint @Inject constructor(
    private val errors: DiagnosticLog,
    private val storage: StorageLocations,
) {
    fun errors() = errors.report()
    fun clear() = errors.clear()
    fun locations() = storage.report()
}
