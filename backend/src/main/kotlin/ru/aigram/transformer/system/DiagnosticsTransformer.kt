package ru.aigram.transformer.system

import org.springframework.stereotype.Component
import ru.aigram.contract.dto.DiagnosticEntry
import ru.aigram.contract.dto.DiagnosticReport
import ru.aigram.contract.dto.DiagnosticSource
import ru.aigram.domain.events.diagnostics.DiagnosticReport as DomainDiagnosticReport

@Component
class DiagnosticsTransformer {
    fun toContract(value: DomainDiagnosticReport): DiagnosticReport = DiagnosticReport(
        value.errors.stream().map {
            DiagnosticEntry(
                it.id, it.date, it.firstOccurredAt, DiagnosticSource.forValue(it.source), it.code, it.message,
                it.remediation, it.exceptionType, it.occurrences, it.metadata,
            )
        }.toList(),
    )
}
