package ru.aigram.domain.events.diagnostics

import kotlinx.serialization.Serializable

@Serializable
data class DiagnosticReport(val errors: List<DiagnosticEntry>)
