package ru.aigram.component.system

import org.springframework.stereotype.Component
import ru.aigram.diagnostics.DiagnosticLog
import ru.aigram.storage.StorageLocations

@Component
class DiagnosticsComponent(private val errors: DiagnosticLog, private val storage: StorageLocations) {
    fun errors() = errors.report()
    fun clear() = errors.clear()
    fun locations() = storage.report()
}
