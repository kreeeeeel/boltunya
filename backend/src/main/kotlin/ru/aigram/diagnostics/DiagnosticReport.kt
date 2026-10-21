package ru.aigram.diagnostics

import kotlinx.serialization.Serializable

@Serializable
data class DiagnosticReport(val errors: List<DiagnosticEntry>)
