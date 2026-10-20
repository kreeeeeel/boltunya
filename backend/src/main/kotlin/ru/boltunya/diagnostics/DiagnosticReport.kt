package ru.boltunya.diagnostics

import kotlinx.serialization.Serializable

@Serializable
data class DiagnosticReport(val errors: List<DiagnosticEntry>)
